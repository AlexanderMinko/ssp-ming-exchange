package com.ming.sspexchange.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.service.auction.WinningBid;

class BidResponseMapperTest {

    private final BidResponseMapper mapper = Mappers.getMapper(BidResponseMapper.class);

    private static final Bidder ALPHA = Bidder.builder().id("alpha").name("alpha").seat("seat-alpha").build();

    private static BidRequest request() {
        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));
        return request;
    }

    private static Bid bid(double price, String adm, String nurl) {
        var bid = new Bid();
        bid.setId("bid-9"); bid.setImpid("imp-1"); bid.setPrice(price); bid.setAdm(adm); bid.setNurl(nurl);
        bid.setCrid("cr-1"); bid.setW(320); bid.setH(50); bid.setMtype(1);
        return bid;
    }

    @Test
    void buildsSingleSeatResponseWithSubstitutedMacros() {
        var bid = bid(2.5, "<div>${AUCTION_ID} @ ${AUCTION_PRICE}</div>", "http://dsp/win?p=${AUCTION_PRICE}");

        var response = mapper.toBidResponse(request(), new WinningBid(ALPHA, bid));

        assertThat(response.getId()).isEqualTo("req-1");
        assertThat(response.getCur()).isEqualTo("USD");
        assertThat(response.getSeatbid()).hasSize(1);
        assertThat(response.getSeatbid().get(0).getSeat()).isEqualTo("seat-alpha");
        var winningBid = response.getSeatbid().get(0).getBid().get(0);
        assertThat(winningBid.getAdm()).isEqualTo("<div>req-1 @ 2.5</div>");
        // nurl is returned substituted for the supply SSP to fire later -- the exchange never calls it
        assertThat(winningBid.getNurl()).isEqualTo("http://dsp/win?p=2.5");
        // every other bid field is carried over as is
        assertThat(winningBid.getCrid()).isEqualTo("cr-1");
        assertThat(winningBid.getW()).isEqualTo(320);
        assertThat(winningBid.getMtype()).isEqualTo(1);
    }

    @Test
    void neverMutatesTheBiddersBid() {
        var bid = bid(2.5, "<div>${AUCTION_ID}</div>", "http://dsp/win?p=${AUCTION_PRICE}");

        var winningBid = mapper.toBidResponse(request(), new WinningBid(ALPHA, bid)).getSeatbid().get(0).getBid().get(0);

        assertThat(winningBid).isNotSameAs(bid);
        assertThat(bid.getAdm()).contains("${AUCTION_ID}");
        assertThat(bid.getNurl()).contains("${AUCTION_PRICE}");
    }

    @Test
    void substitutesEveryMacro() {
        var bid = bid(2.5, "${AUCTION_ID}|${AUCTION_BID_ID}|${AUCTION_IMP_ID}|${AUCTION_SEAT_ID}|${AUCTION_PRICE}", null);

        var adm = mapper.toBidResponse(request(), new WinningBid(ALPHA, bid)).getSeatbid().get(0).getBid().get(0).getAdm();

        assertThat(adm).isEqualTo("req-1|bid-9|imp-1|seat-alpha|2.5");
    }

    @Test
    void priceIsPlainDecimalAndNullNurlStaysNull() {
        var out = mapper.toBidResponse(request(), new WinningBid(ALPHA, bid(0.1, "${AUCTION_PRICE}", null)))
                .getSeatbid().get(0).getBid().get(0);

        assertThat(out.getAdm()).isEqualTo("0.1");
        assertThat(out.getNurl()).isNull();
    }
}
