package com.ming.sspexchange.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.RtbData;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.openrtb.Banner;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.model.openrtb.Video;
import com.ming.sspexchange.service.auction.WinningBid;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;

class AuctionEventMapperTest {

    private final AuctionEventMapper mapper = Mappers.getMapper(AuctionEventMapper.class);

    private static final Bidder ALPHA = Bidder.builder().id("alpha").name("alpha").seat("seat-alpha").build();
    private static final Bidder BETA = Bidder.builder().id("beta").name("beta").seat("seat-beta").build();
    private static final SupplyPartner SOURCE = SupplyPartner.builder()
            .id("s-1").entityType(SupplyPartnerType.SOURCE).sourceId("abc").build();
    private static final SupplyPartner PUBLISHER = SupplyPartner.builder()
            .id("p-1").entityType(SupplyPartnerType.PUBLISHER).publisherId("pub-100").build();

    private static RtbContext context(SupplyPartner publisher) {
        var bannerImp = new Imp(); bannerImp.setId("imp-1"); bannerImp.setBanner(new Banner());
        var videoImp = new Imp(); videoImp.setId("imp-2"); videoImp.setVideo(new Video());
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(bannerImp, videoImp));

        var data = new RtbData();
        data.setSourceId("abc");
        data.setSource(SOURCE);
        data.setPublisher(publisher);
        return new RtbContext(request, data);
    }

    private static Bid bid(String impid, double price) {
        var bid = new Bid(); bid.setId("bid-" + impid); bid.setImpid(impid); bid.setPrice(price); bid.setAdm("<div/>");
        return bid;
    }

    private static BidderCallResult bidResult(Bidder bidder, Bid... bids) {
        var seatBid = new SeatBid(); seatBid.setSeat(bidder.getSeat()); seatBid.setBid(List.of(bids));
        var response = new BidResponse(); response.setId("req-1"); response.setSeatbid(List.of(seatBid));
        return new BidderCallResult(bidder, BidderOutcome.BID, response, 40);
    }

    @Test
    void mapsFillWithPublisherConfig() {
        var ctx = context(PUBLISHER);
        var winningBid = bid("imp-1", 2.5);
        ctx.data().setBidderResults(List.of(
                bidResult(ALPHA, winningBid, bid("imp-2", 9.0)),   // imp-2 price must not leak into the log
                new BidderCallResult(BETA, BidderOutcome.TIMEOUT, null, 300)));
        ctx.data().setWinner(new WinningBid(ALPHA, winningBid));

        var event = mapper.toEvent(ctx, 200, 55);

        assertThat(event.auctionId()).isEqualTo("req-1");
        assertThat(event.sourceId()).isEqualTo("abc");
        assertThat(event.publisherId()).isEqualTo("pub-100");
        assertThat(event.supplierType()).isEqualTo("PUBLISHER");
        assertThat(event.formats()).containsExactly("BANNER", "VIDEO");
        assertThat(event.eligibleBidders()).containsExactly("alpha", "beta");   // one per call result
        assertThat(event.bidders()).extracting("name", "outcome", "latencyMs", "price")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alpha", "BID", 40L, 2.5),
                        org.assertj.core.groups.Tuple.tuple("beta", "TIMEOUT", 300L, null));
        assertThat(event.winner()).isEqualTo("alpha");
        assertThat(event.clearingPrice()).isEqualTo(2.5);
        assertThat(event.responseStatus()).isEqualTo(200);
        assertThat(event.totalLatencyMs()).isEqualTo(55);
    }

    @Test
    void mapsNoBidWithSourceConfig() {
        var ctx = context(null);

        var event = mapper.toEvent(ctx, 204, 12);

        assertThat(event.publisherId()).isNull();
        assertThat(event.supplierType()).isEqualTo("SOURCE");
        assertThat(event.eligibleBidders()).isEmpty();
        assertThat(event.bidders()).isEmpty();
        assertThat(event.winner()).isNull();
        assertThat(event.clearingPrice()).isNull();
        assertThat(event.responseStatus()).isEqualTo(204);
    }
}
