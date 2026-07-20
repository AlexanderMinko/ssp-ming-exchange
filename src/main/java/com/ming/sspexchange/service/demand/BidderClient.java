package com.ming.sspexchange.service.demand;

import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.ming.sspexchange.model.openrtb.BidResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidderClient {

    private final RestClient bidderRestClient;

    public BidderCallResult call(OutboundRequest out) {
        long start = System.nanoTime();
        try {
            var entity = bidderRestClient.post()
                    .uri(out.bidder().getEndpoint())
                    .body(out.request())
                    .retrieve()
                    .toEntity(BidResponse.class);
            var body = entity.getBody();
            var outcome = Objects.isNull(body) || Objects.isNull(body.getSeatbid()) || body.getSeatbid().isEmpty()
                    ? BidderOutcome.NO_BID : BidderOutcome.BID;
            return new BidderCallResult(out.bidder(), outcome, body, Latency.msSince(start));
        } catch (ResourceAccessException e) {
            var outcome = isTimeout(e) ? BidderOutcome.TIMEOUT : BidderOutcome.ERROR;
            return new BidderCallResult(out.bidder(), outcome, null, Latency.msSince(start));
        } catch (RuntimeException e) {
            // Isolate any per-bidder failure (4xx/5xx RestClientException, dsl-json
            // HttpMessageConversionException on a bad body, etc.) so one bidder never breaks the auction.
            log.debug("bidder {} error: {}", out.bidder().getName(), e.toString());
            return new BidderCallResult(out.bidder(), BidderOutcome.ERROR, null, Latency.msSince(start));
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; Objects.nonNull(t); t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof TimeoutException) return true;
        }
        return false;
    }
}
