package com.ming.sspexchange.service.auction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.service.demand.BidderCallResult;

import lombok.RequiredArgsConstructor;

/**
 * The auction: picks the winner among the valid bids (first price, first imp only in v1).
 * Which bids are valid is {@link BidResponseValidationService}'s call.
 */
@Component
@RequiredArgsConstructor
public class AuctionService {

    private final BidResponseValidationService bidResponseValidation;

    /** Highest valid bid on the first imp. Stream.max keeps the first element on a tie, so the earliest bidder wins equal prices. */
    public Optional<WinningBid> selectWinner(BidRequest request, List<BidderCallResult> results) {
        var firstImp = request.getImp().get(0);
        return results.stream()
                .flatMap(result -> bidResponseValidation.validBids(firstImp, result)
                        .map(bid -> new WinningBid(result.bidder(), bid)))
                .max(Comparator.comparingDouble(winningBid -> winningBid.bid().getPrice()));
    }
}
