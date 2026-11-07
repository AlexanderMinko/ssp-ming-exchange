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

class RequestValidationServiceTest {

    private final RequestValidationService validator = new RequestValidationService();

    private static BidRequest valid() {
        var banner = new Banner(); banner.setW(320); banner.setH(50);
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var pub = new Publisher(); pub.setId("pub-100");
        var app = new App(); app.setPublisher(pub);
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp)); request.setApp(app);
        return request;
    }

    @Test void acceptsValidRequest() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test void rejectsMissingId() {
        var request = valid(); request.setId(" ");
        assertThat(validator.validate(request)).contains(RejectReason.MISSING_REQUEST_ID);
    }

    @Test void rejectsEmptyImps() {
        var request = valid(); request.setImp(List.of());
        assertThat(validator.validate(request)).contains(RejectReason.MISSING_IMP);
    }

    @Test void rejectsImpWithoutBannerOrVideo() {
        var imp = new Imp(); imp.setId("imp-1");
        var request = valid(); request.setImp(List.of(imp));
        assertThat(validator.validate(request)).contains(RejectReason.IMP_NO_FORMAT);
    }

    @Test void rejectsNegativeFloor() {
        var request = valid(); request.getImp().get(0).setBidfloor(-1.0);
        assertThat(validator.validate(request)).contains(RejectReason.NEGATIVE_BIDFLOOR);
    }

    @Test void rejectsBothAppAndSite() {
        var request = valid(); request.setSite(new Site());
        assertThat(validator.validate(request)).contains(RejectReason.APP_XOR_SITE);
    }

    @Test void rejectsNeitherAppNorSite() {
        var request = valid(); request.setApp(null);
        assertThat(validator.validate(request)).contains(RejectReason.APP_XOR_SITE);
    }
}
