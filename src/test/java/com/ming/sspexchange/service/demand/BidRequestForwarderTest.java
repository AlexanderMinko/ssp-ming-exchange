package com.ming.sspexchange.service.demand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.service.mapper.OrtbMapper;

class BidRequestForwarderTest {

    private final BidRequestForwarder forwarder =
            new BidRequestForwarder(Mappers.getMapper(OrtbMapper.class));

    @Test
    void copiesRequestWithAdjustedTmaxLeavingOriginalUntouched() {
        var imp = new Imp(); imp.setId("imp-1");
        var app = new App(); app.setBundle("com.demo.app");
        var in = new BidRequest(); in.setId("req-1"); in.setTmax(350); in.setImp(List.of(imp)); in.setApp(app);
        var bidder = BidderEntity.builder().id("b").name("b").endpoint("http://x").build();

        var out = forwarder.outbound(in, bidder, 350);

        assertThat(out.timeoutMs()).isEqualTo(300);
        assertThat(out.request().getTmax()).isEqualTo(300);
        assertThat(out.request().getId()).isEqualTo("req-1");
        assertThat(out.request()).isNotSameAs(in);                       // top-level object is a copy
        assertThat(out.request().getImp().get(0)).isSameAs(imp);         // nested objects shared by reference
        assertThat(out.request().getApp()).isSameAs(app);
        assertThat(in.getTmax()).isEqualTo(350);                         // original untouched
    }

    @Test
    void bidderOverrideCapsTimeout() {
        var in = new BidRequest(); in.setId("req-1"); in.setImp(List.of());
        var bidder = BidderEntity.builder().id("b").name("b").tmaxMs(150).build();

        assertThat(forwarder.outbound(in, bidder, 350).timeoutMs()).isEqualTo(150);
    }
}
