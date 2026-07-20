package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

class WinnerSelectorTest {

    private final WinnerSelector selector = new WinnerSelector();

    private static BidderCallResult bidResult(String bidderName, String impid, Double price, String adm) {
        var bid = new Bid(); bid.setId("bid-" + bidderName); bid.setImpid(impid); bid.setPrice(price); bid.setAdm(adm);
        var sb = new SeatBid(); sb.setSeat("seat-" + bidderName); sb.setBid(List.of(bid));
        var resp = new BidResponse(); resp.setId("req-1"); resp.setSeatbid(List.of(sb));
        var bidder = BidderEntity.builder().id(bidderName).name(bidderName).seat("seat-" + bidderName).build();
        return new BidderCallResult(bidder, BidderOutcome.BID, resp, 50);
    }

    private static BidRequest requestWithFloor(Double floor) {
        var imp = new Imp(); imp.setId("imp-1"); imp.setBidfloor(floor);
        var r = new BidRequest(); r.setId("req-1"); r.setImp(List.of(imp));
        return r;
    }

    @Test
    void highestPriceWins() {
        var results = List.of(
                bidResult("alpha", "imp-1", 1.0, "<a/>"),
                bidResult("beta", "imp-1", 2.5, "<b/>"));
        var winner = selector.select(requestWithFloor(null), results);
        assertThat(winner).isPresent();
        assertThat(winner.get().bidder().getName()).isEqualTo("beta");
    }

    @Test
    void firstReceivedWinsTies() {
        var results = List.of(
                bidResult("alpha", "imp-1", 2.0, "<a/>"),
                bidResult("beta", "imp-1", 2.0, "<b/>"));
        assertThat(selector.select(requestWithFloor(null), results).get().bidder().getName())
                .isEqualTo("alpha");
    }

    @Test
    void underFloorIsFilteredOut() {
        var results = List.of(bidResult("alpha", "imp-1", 0.4, "<a/>"));
        assertThat(selector.select(requestWithFloor(0.5), results)).isEmpty();
    }

    @Test
    void wrongImpidIsFilteredOut() {
        var results = List.of(bidResult("alpha", "imp-2", 3.0, "<a/>"));
        assertThat(selector.select(requestWithFloor(null), results)).isEmpty();
    }

    @Test
    void missingAdmIsFilteredOut() {
        var results = List.of(bidResult("alpha", "imp-1", 3.0, " "));
        assertThat(selector.select(requestWithFloor(null), results)).isEmpty();
    }

    @Test
    void nonBidOutcomesAreIgnored() {
        var bidder = BidderEntity.builder().id("x").name("x").build();
        var results = List.of(new BidderCallResult(bidder, BidderOutcome.TIMEOUT, null, 300));
        assertThat(selector.select(requestWithFloor(null), results)).isEmpty();
    }
}
