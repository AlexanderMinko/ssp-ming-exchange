package com.ming.sspexchange.service.demand;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidResponse;

/** What one bidder answered. {@code response} is non-null only for a {@link BidderOutcome#BID}. */
public record BidderCallResult(Bidder bidder, BidderOutcome outcome, BidResponse response, long latencyMs) {

    /** Every bid across all seats, empty when there is no response. */
    public Stream<Bid> bids() {
        if (Objects.isNull(response) || ObjectUtils.isEmpty(response.getSeatbid())) {
            return Stream.empty();
        }
        return response.getSeatbid().stream()
                .filter(seatBid -> ObjectUtils.isNotEmpty(seatBid.getBid()))
                .flatMap(seatBid -> seatBid.getBid().stream());
    }

    /** Best price this bidder offered on one imp, so a logged price matches what actually competed. */
    public OptionalDouble bestPrice(String impId) {
        return bids()
                .filter(bid -> impId.equals(bid.getImpid()))
                .map(Bid::getPrice)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max();
    }
}
