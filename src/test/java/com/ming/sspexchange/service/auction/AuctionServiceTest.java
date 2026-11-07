package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

class AuctionServiceTest {

    private final AuctionService auction = new AuctionService(new BidResponseValidationService());

    private static BidderCallResult bidResult(String bidderName, String impid, Double price, String adm) {
        var bid = new Bid(); bid.setId("bid-" + bidderName); bid.setImpid(impid); bid.setPrice(price); bid.setAdm(adm);
        var seatBid = new SeatBid(); seatBid.setSeat("seat-" + bidderName); seatBid.setBid(List.of(bid));
        var response = new BidResponse(); response.setId("req-1"); response.setSeatbid(List.of(seatBid));
        var bidder = Bidder.builder().id(bidderName).name(bidderName).seat("seat-" + bidderName).build();
        return new BidderCallResult(bidder, BidderOutcome.BID, response, 50);
    }

    private static BidRequest requestWithFloor(Double floor) {
        var imp = new Imp(); imp.setId("imp-1"); imp.setBidfloor(floor);
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));
        return request;
    }

    @Nested
    class SelectWinner {

        @Test
        void highestPriceWins() {
            var results = List.of(
                    bidResult("alpha", "imp-1", 1.0, "<a/>"),
                    bidResult("beta", "imp-1", 2.5, "<b/>"));
            var winner = auction.selectWinner(requestWithFloor(null), results);
            assertThat(winner).isPresent();
            assertThat(winner.get().bidder().getName()).isEqualTo("beta");
        }

        @Test
        void firstReceivedWinsTies() {
            var results = List.of(
                    bidResult("alpha", "imp-1", 2.0, "<a/>"),
                    bidResult("beta", "imp-1", 2.0, "<b/>"));
            assertThat(auction.selectWinner(requestWithFloor(null), results).get().bidder().getName())
                    .isEqualTo("alpha");
        }

        @Test
        void onlyValidBidsCompete() {
            // Validation rules themselves are covered by BidResponseValidationServiceTest.
            var results = List.of(
                    bidResult("alpha", "imp-1", 0.4, "<a/>"),   // under floor
                    bidResult("beta", "imp-1", 1.0, "<b/>"));
            assertThat(auction.selectWinner(requestWithFloor(0.5), results).get().bidder().getName())
                    .isEqualTo("beta");
        }

        @Test
        void noValidBidsMeansNoWinner() {
            var bidder = Bidder.builder().id("x").name("x").build();
            var results = List.of(new BidderCallResult(bidder, BidderOutcome.TIMEOUT, null, 300));
            assertThat(auction.selectWinner(requestWithFloor(null), results)).isEmpty();
        }
    }
}
