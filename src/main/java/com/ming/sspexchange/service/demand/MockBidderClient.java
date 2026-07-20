package com.ming.sspexchange.service.demand;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.SeatBid;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MockBidderClient {

    // In-process stand-in for a DSP until the real dummy-DSP service exists. Fabricated bids go
    // through the exact same auction/macro/event/metric path as HTTP bids.
    public BidderCallResult call(OutboundRequest out) {
        long start = System.nanoTime();
        var mock = out.bidder().getMock();
        if (Objects.isNull(mock) || Objects.isNull(mock.getPrice())) {
            log.warn("mock bidder {} has no mock config", out.bidder().getName());
            return new BidderCallResult(out.bidder(), BidderOutcome.ERROR, null, 0);
        }
        simulateLatency(mock.getLatencyMs());
        double bidRate = Objects.nonNull(mock.getBidRate()) ? mock.getBidRate() : 1.0;
        if (ThreadLocalRandom.current().nextDouble() >= bidRate) {
            return new BidderCallResult(out.bidder(), BidderOutcome.NO_BID, null, Latency.msSince(start));
        }
        return new BidderCallResult(out.bidder(), BidderOutcome.BID,
                fabricate(out, mock.getPrice()), Latency.msSince(start));
    }

    private static BidResponse fabricate(OutboundRequest out, double price) {
        var request = out.request();
        var bid = new Bid();
        bid.setId("mock-%s-%s".formatted(out.bidder().getName(), request.getId()));
        bid.setImpid(request.getImp().get(0).getId());
        bid.setPrice(price);
        bid.setAdm("<div>mock ad from %s | auction ${AUCTION_ID} | price ${AUCTION_PRICE}</div>"
                .formatted(out.bidder().getName()));
        bid.setNurl("https://mock-dsp.invalid/win?bidder=%s&price=${AUCTION_PRICE}"
                .formatted(out.bidder().getName()));
        bid.setCrid("mock-crid-" + out.bidder().getName());
        bid.setAdomain(List.of("mock-advertiser.example"));

        var seatBid = new SeatBid();
        seatBid.setSeat(out.bidder().getSeat());
        seatBid.setBid(List.of(bid));

        var response = new BidResponse();
        response.setId(request.getId());
        response.setCur("USD");
        response.setSeatbid(List.of(seatBid));
        return response;
    }

    private static void simulateLatency(Integer latencyMs) {
        if (Objects.isNull(latencyMs) || latencyMs <= 0) return;
        try {
            Thread.sleep(latencyMs);   // virtual thread -- cheap; fan-out cancels it on deadline
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
