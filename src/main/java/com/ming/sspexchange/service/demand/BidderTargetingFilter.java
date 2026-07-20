package com.ming.sspexchange.service.demand;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

@Component
public class BidderTargetingFilter {

    public List<BidderEntity> eligible(BidRequest request, List<BidderEntity> all) {
        var formats = requestFormats(request);
        var country = requestCountry(request);
        return all.stream()
                .filter(b -> b.getStatus() == PartnerStatus.ACTIVE)
                .filter(b -> formats(b).stream().anyMatch(formats::contains))
                .filter(b -> Objects.isNull(country) || countries(b).isEmpty() || countries(b).contains(country))
                .toList();
    }

    public static Set<AdFormat> requestFormats(BidRequest r) {
        var formats = EnumSet.noneOf(AdFormat.class);
        for (Imp imp : r.getImp()) {
            if (Objects.nonNull(imp.getBanner())) formats.add(AdFormat.BANNER);
            if (Objects.nonNull(imp.getVideo())) formats.add(AdFormat.VIDEO);
        }
        return formats;
    }

    private static String requestCountry(BidRequest r) {
        return Objects.nonNull(r.getDevice()) && Objects.nonNull(r.getDevice().getGeo())
                ? r.getDevice().getGeo().getCountry() : null;
    }

    private static Set<AdFormat> formats(BidderEntity b) {
        return Objects.isNull(b.getTargeting()) || Objects.isNull(b.getTargeting().getFormats())
                ? Set.of() : b.getTargeting().getFormats();
    }

    private static Set<String> countries(BidderEntity b) {
        return Objects.isNull(b.getTargeting()) || Objects.isNull(b.getTargeting().getCountries())
                ? Set.of() : b.getTargeting().getCountries();
    }
}
