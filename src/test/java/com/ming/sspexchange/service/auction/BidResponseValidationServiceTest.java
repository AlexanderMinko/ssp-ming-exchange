package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

class BidResponseValidationServiceTest {

    private final BidResponseValidationService validation = new BidResponseValidationService();

    private static final Bidder ALPHA = Bidder.builder().id("alpha").name("alpha").seat("seat-alpha").build();

    private static Bid bid(String impid, Double price, String adm) {
        var bid = new Bid(); bid.setId("bid-" + impid); bid.setImpid(impid); bid.setPrice(price); bid.setAdm(adm);
        return bid;
    }

    private static BidderCallResult bidResult(Bid... bids) {
        var seatBid = new SeatBid(); seatBid.setSeat("seat-alpha"); seatBid.setBid(List.of(bids));
        var response = new BidResponse(); response.setId("req-1"); response.setSeatbid(List.of(seatBid));
        return new BidderCallResult(ALPHA, BidderOutcome.BID, response, 50);
    }

    private static Imp imp(Double floor) {
        var imp = new Imp(); imp.setId("imp-1"); imp.setBidfloor(floor);
        return imp;
    }

    @Test
    void keepsBidsOnTheImpAtOrAboveFloorWithMarkup() {
        var result = bidResult(bid("imp-1", 0.5, "<a/>"), bid("imp-1", 2.0, "<b/>"));

        assertThat(validation.validBids(imp(0.5), result)).extracting(Bid::getPrice).containsExactly(0.5, 2.0);
    }

    @Test
    void nullFloorMeansZero() {
        assertThat(validation.validBids(imp(null), bidResult(bid("imp-1", 0.01, "<a/>")))).hasSize(1);
    }

    @Test
    void underFloorIsDropped() {
        assertThat(validation.validBids(imp(0.5), bidResult(bid("imp-1", 0.4, "<a/>")))).isEmpty();
    }

    @Test
    void missingPriceIsDropped() {
        assertThat(validation.validBids(imp(null), bidResult(bid("imp-1", null, "<a/>")))).isEmpty();
    }

    @Test
    void wrongImpidIsDropped() {
        assertThat(validation.validBids(imp(null), bidResult(bid("imp-2", 3.0, "<a/>")))).isEmpty();
    }

    @Test
    void missingAdmIsDropped() {
        assertThat(validation.validBids(imp(null), bidResult(bid("imp-1", 3.0, " ")))).isEmpty();
        assertThat(validation.validBids(imp(null), bidResult(bid("imp-1", 3.0, null)))).isEmpty();
    }

    @Test
    void nonBidOutcomesYieldNothing() {
        var timeout = new BidderCallResult(ALPHA, BidderOutcome.TIMEOUT, null, 300);
        assertThat(validation.validBids(imp(null), timeout)).isEmpty();
    }
}
