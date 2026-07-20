package com.ming.sspexchange.service.supply;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

@Component
public class RequestValidator {

    public void validate(BidRequest r) {
        require(Objects.nonNull(r.getId()) && !r.getId().isBlank(), "missing request id");
        require(Objects.nonNull(r.getImp()) && !r.getImp().isEmpty(), "missing imp");
        require(Objects.nonNull(r.getApp()) ^ Objects.nonNull(r.getSite()), "exactly one of app/site required");
        for (Imp imp : r.getImp()) {
            require(Objects.nonNull(imp.getId()) && !imp.getId().isBlank(), "imp missing id");
            require(Objects.nonNull(imp.getBanner()) || Objects.nonNull(imp.getVideo()), "imp requires banner or video");
            require(Objects.isNull(imp.getBidfloor()) || imp.getBidfloor() >= 0, "negative bidfloor");
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new InvalidBidRequestException(reason);
    }
}
