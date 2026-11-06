package com.ming.sspexchange.service.supply;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

/** Returns the first rule the request breaks, or empty when it is valid. Never throws. */
@Component
public class RequestValidator {

    public Optional<RejectReason> validate(BidRequest r) {
        if (StringUtils.isBlank(r.getId())) return Optional.of(RejectReason.MISSING_REQUEST_ID);
        if (ObjectUtils.isEmpty(r.getImp())) return Optional.of(RejectReason.MISSING_IMP);
        if (Objects.nonNull(r.getApp()) == Objects.nonNull(r.getSite())) return Optional.of(RejectReason.APP_XOR_SITE);
        return r.getImp().stream()
                .map(RequestValidator::checkImp)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<RejectReason> checkImp(Imp imp) {
        if (StringUtils.isBlank(imp.getId())) return Optional.of(RejectReason.IMP_MISSING_ID);
        if (Objects.isNull(imp.getBanner()) && Objects.isNull(imp.getVideo())) return Optional.of(RejectReason.IMP_NO_FORMAT);
        if (Objects.nonNull(imp.getBidfloor()) && imp.getBidfloor() < 0) return Optional.of(RejectReason.NEGATIVE_BIDFLOOR);
        return Optional.empty();
    }
}
