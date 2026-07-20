package com.ming.sspexchange.service.supply;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.Banner;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.Site;

class RequestValidatorTest {

    private final RequestValidator validator = new RequestValidator();

    private static BidRequest valid() {
        var banner = new Banner(); banner.setW(320); banner.setH(50);
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var pub = new Publisher(); pub.setId("pub-100");
        var app = new App(); app.setPublisher(pub);
        var r = new BidRequest(); r.setId("req-1"); r.setImp(List.of(imp)); r.setApp(app);
        return r;
    }

    @Test void acceptsValidRequest() {
        assertThatCode(() -> validator.validate(valid())).doesNotThrowAnyException();
    }

    @Test void rejectsMissingId() {
        var r = valid(); r.setId(" ");
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }

    @Test void rejectsEmptyImps() {
        var r = valid(); r.setImp(List.of());
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }

    @Test void rejectsImpWithoutBannerOrVideo() {
        var imp = new Imp(); imp.setId("imp-1");
        var r = valid(); r.setImp(List.of(imp));
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }

    @Test void rejectsNegativeFloor() {
        var r = valid(); r.getImp().get(0).setBidfloor(-1.0);
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }

    @Test void rejectsBothAppAndSite() {
        var r = valid(); r.setSite(new Site());
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }

    @Test void rejectsNeitherAppNorSite() {
        var r = valid(); r.setApp(null);
        assertThatThrownBy(() -> validator.validate(r)).isInstanceOf(InvalidBidRequestException.class);
    }
}
