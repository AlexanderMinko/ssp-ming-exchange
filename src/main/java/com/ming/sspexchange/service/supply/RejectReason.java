package com.ming.sspexchange.service.supply;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Why a request was rejected before the auction. Each reason carries its HTTP status. */
@Getter
@RequiredArgsConstructor
public enum RejectReason {
    // supply (SupplyResolver)
    MISSING_SOURCE(403),
    BAD_SOURCE_ID(400),
    UNKNOWN_SOURCE(403),
    PAUSED_PUBLISHER(403),
    // ORTB validation (RequestValidator)
    MISSING_REQUEST_ID(400),
    MISSING_IMP(400),
    APP_XOR_SITE(400),
    IMP_MISSING_ID(400),
    IMP_NO_FORMAT(400),
    NEGATIVE_BIDFLOOR(400);

    private final int httpStatus;
}
