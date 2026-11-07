package com.ming.sspexchange.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

class OrtbMapperTest {

    private final OrtbMapper mapper = Mappers.getMapper(OrtbMapper.class);

    @Test
    void copiesRequestWithAdjustedTmaxLeavingOriginalUntouched() {
        var imp = new Imp(); imp.setId("imp-1");
        var app = new App(); app.setBundle("com.demo.app");
        var in = new BidRequest(); in.setId("req-1"); in.setTmax(350); in.setImp(List.of(imp)); in.setApp(app);

        var out = mapper.copyWithTmax(in, 300);

        assertThat(out.getTmax()).isEqualTo(300);
        assertThat(out.getId()).isEqualTo("req-1");
        assertThat(out).isNotSameAs(in);                       // top-level object is a copy
        assertThat(out.getImp().get(0)).isSameAs(imp);         // nested objects shared by reference
        assertThat(out.getApp()).isSameAs(app);
        assertThat(in.getTmax()).isEqualTo(350);               // original untouched
    }
}
