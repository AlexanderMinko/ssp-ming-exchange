package com.ming.sspexchange.service.demand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.Targeting;
import com.ming.sspexchange.model.openrtb.Banner;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Device;
import com.ming.sspexchange.model.openrtb.Geo;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.Video;

class BidderTargetingFilterTest {

    private final BidderTargetingFilter filter = new BidderTargetingFilter();

    private static BidderEntity bidder(String name, PartnerStatus status, Set<AdFormat> formats, Set<String> countries) {
        return BidderEntity.builder().id(name).name(name).seat("seat-" + name).endpoint("http://x/" + name)
                .status(status)
                .targeting(Targeting.builder().formats(formats).countries(countries).build())
                .build();
    }

    private static BidRequest bannerRequest(String country) {
        var banner = new Banner();
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var r = new BidRequest(); r.setId("req-1"); r.setImp(List.of(imp));
        if (Objects.nonNull(country)) {
            var geo = new Geo(); geo.setCountry(country);
            var device = new Device(); device.setGeo(geo);
            r.setDevice(device);
        }
        return r;
    }

    @Test
    void filtersByStatusFormatAndCountry() {
        var all = List.of(
                bidder("active-banner-all", PartnerStatus.ACTIVE, Set.of(AdFormat.BANNER), Set.of()),
                bidder("active-banner-usa", PartnerStatus.ACTIVE, Set.of(AdFormat.BANNER), Set.of("USA")),
                bidder("active-video-only", PartnerStatus.ACTIVE, Set.of(AdFormat.VIDEO), Set.of()),
                bidder("paused",            PartnerStatus.PAUSED, Set.of(AdFormat.BANNER), Set.of()));

        var eligible = filter.eligible(bannerRequest("DEU"), all);

        assertThat(eligible).extracting(BidderEntity::getName).containsExactly("active-banner-all");
    }

    @Test
    void requestWithoutCountryMatchesAllCountryTargets() {
        var all = List.of(bidder("usa-only", PartnerStatus.ACTIVE, Set.of(AdFormat.BANNER), Set.of("USA")));
        assertThat(filter.eligible(bannerRequest(null), all)).hasSize(1);
    }

    @Test
    void matchingCountryPasses() {
        var all = List.of(bidder("usa-only", PartnerStatus.ACTIVE, Set.of(AdFormat.BANNER), Set.of("USA")));
        assertThat(filter.eligible(bannerRequest("USA"), all)).hasSize(1);
    }

    @Test
    void mixedImpFormatsUnion() {
        var video = new Video();
        var imp2 = new Imp(); imp2.setId("imp-2"); imp2.setVideo(video);
        var r = bannerRequest(null);
        r.setImp(List.of(r.getImp().get(0), imp2));

        var all = List.of(bidder("video-only", PartnerStatus.ACTIVE, Set.of(AdFormat.VIDEO), Set.of()));
        assertThat(filter.eligible(r, all)).hasSize(1);
    }

    @Test
    void bidderWithoutTargetingIsSkipped() {
        var b = BidderEntity.builder().id("x").name("x").status(PartnerStatus.ACTIVE).build(); // null targeting
        assertThat(filter.eligible(bannerRequest(null), List.of(b))).isEmpty();
    }
}
