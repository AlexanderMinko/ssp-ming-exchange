package com.ming.sspexchange.service.auction;

import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

/**
 * Which of a bidder's bids may compete for an imp. A bid is valid when it targets that imp, carries a
 * price at or above the imp's floor, and has creative markup. Everything else is dropped silently:
 * a bad bid is the bidder's problem, never a thrown error on the hot path.
 */
@Component
public class BidResponseValidationService {

    public Stream<Bid> validBids(Imp imp, BidderCallResult result) {
        if (result.outcome() != BidderOutcome.BID) {
            return Stream.empty();
        }
        double floor = Objects.nonNull(imp.getBidfloor()) ? imp.getBidfloor() : 0.0;
        return result.bids()
                .filter(bid -> imp.getId().equals(bid.getImpid()))
                .filter(bid -> Objects.nonNull(bid.getPrice()) && bid.getPrice() >= floor)
                .filter(bid -> StringUtils.isNotBlank(bid.getAdm()));
    }
}
