package com.ming.sspexchange.service.supply;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test void rejectsMissingId() {
        var r = valid(); r.setId(" ");
        assertThat(validator.validate(r)).contains(RejectReason.MISSING_REQUEST_ID);
    }

    @Test void rejectsEmptyImps() {
        var r = valid(); r.setImp(List.of());
        assertThat(validator.validate(r)).contains(RejectReason.MISSING_IMP);
    }

    @Test void rejectsImpWithoutBannerOrVideo() {
        var imp = new Imp(); imp.setId("imp-1");
        var r = valid(); r.setImp(List.of(imp));
        assertThat(validator.validate(r)).contains(RejectReason.IMP_NO_FORMAT);
    }

    @Test void rejectsNegativeFloor() {
        var r = valid(); r.getImp().get(0).setBidfloor(-1.0);
        assertThat(validator.validate(r)).contains(RejectReason.NEGATIVE_BIDFLOOR);
    }

    @Test void rejectsBothAppAndSite() {
        var r = valid(); r.setSite(new Site());
        assertThat(validator.validate(r)).contains(RejectReason.APP_XOR_SITE);
    }

    @Test void rejectsNeitherAppNorSite() {
        var r = valid(); r.setApp(null);
        assertThat(validator.validate(r)).contains(RejectReason.APP_XOR_SITE);
    }
}
