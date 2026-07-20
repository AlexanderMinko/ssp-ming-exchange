package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.service.mapper.OrtbMapper;

class ResponseBuilderTest {

    private final ResponseBuilder builder =
            new ResponseBuilder(new MacroProcessor(), Mappers.getMapper(OrtbMapper.class));

    @Test
    void buildsSingleSeatResponseWithSubstitutedMacros() {
        var bid = new Bid();
        bid.setId("bid-9"); bid.setImpid("imp-1"); bid.setPrice(2.5);
        bid.setAdm("<div>${AUCTION_ID} @ ${AUCTION_PRICE}</div>");
        bid.setNurl("http://dsp/win?p=${AUCTION_PRICE}");
        var bidder = BidderEntity.builder().id("alpha").name("alpha").seat("seat-alpha").build();

        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));

        var response = builder.build(request, new WinningBid(bidder, bid));

        assertThat(response.getId()).isEqualTo("req-1");
        assertThat(response.getCur()).isEqualTo("USD");
        assertThat(response.getSeatbid()).hasSize(1);
        assertThat(response.getSeatbid().get(0).getSeat()).isEqualTo("seat-alpha");
        var winningBid = response.getSeatbid().get(0).getBid().get(0);
        assertThat(winningBid.getAdm()).isEqualTo("<div>req-1 @ 2.5</div>");
        // nurl is returned substituted for the supply SSP to fire later -- the exchange never calls it
        assertThat(winningBid.getNurl()).isEqualTo("http://dsp/win?p=2.5");
        // the bidder's original bid was copied via MapStruct, not mutated
        assertThat(winningBid).isNotSameAs(bid);
        assertThat(bid.getAdm()).contains("${AUCTION_ID}");
        assertThat(bid.getNurl()).contains("${AUCTION_PRICE}");
    }
}
