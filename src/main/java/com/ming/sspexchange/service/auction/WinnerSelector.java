package com.ming.sspexchange.service.auction;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

@Component
public class WinnerSelector {

    // First-price auction over the FIRST imp only (v1 scope).
    public Optional<WinningBid> select(BidRequest request, List<BidderCallResult> results) {
        var firstImp = request.getImp().get(0);
        double floor = Objects.nonNull(firstImp.getBidfloor()) ? firstImp.getBidfloor() : 0.0;

        // Stream.max keeps the first element on a tie, so the earliest bidder wins equal prices.
        return results.stream()
                .filter(result -> result.outcome() == BidderOutcome.BID)
                .flatMap(result -> result.response().getSeatbid().stream()
                        .filter(seatBid -> Objects.nonNull(seatBid.getBid()))
                        .flatMap(seatBid -> seatBid.getBid().stream())
                        .filter(bid -> firstImp.getId().equals(bid.getImpid()))
                        .filter(bid -> Objects.nonNull(bid.getPrice()) && bid.getPrice() >= floor)
                        .filter(bid -> StringUtils.isNotBlank(bid.getAdm()))
                        .map(bid -> new WinningBid(result.bidder(), bid)))
                .max(Comparator.comparingDouble(w -> w.bid().getPrice()));
    }
}
