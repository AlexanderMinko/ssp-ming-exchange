package com.ming.sspexchange.service.auction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

@Component
public class WinnerSelector {

    // First-price auction over the FIRST imp only (v1 scope).
    public Optional<WinningBid> select(BidRequest request, List<BidderCallResult> results) {
        var firstImp = request.getImp().get(0);
        double floor = Objects.nonNull(firstImp.getBidfloor()) ? firstImp.getBidfloor() : 0.0;

        WinningBid best = null;
        for (BidderCallResult result : results) {
            if (result.outcome() != BidderOutcome.BID) continue;
            for (SeatBid seatBid : result.response().getSeatbid()) {
                if (Objects.isNull(seatBid.getBid())) continue;
                for (Bid bid : seatBid.getBid()) {
                    if (!firstImp.getId().equals(bid.getImpid())) continue;
                    if (Objects.isNull(bid.getPrice()) || bid.getPrice() < floor) continue;
                    if (Objects.isNull(bid.getAdm()) || bid.getAdm().isBlank()) continue;
                    if (Objects.isNull(best) || bid.getPrice() > best.bid().getPrice()) {
                        best = new WinningBid(result.bidder(), bid);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
