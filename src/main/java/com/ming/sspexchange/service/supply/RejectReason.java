package com.ming.sspexchange.service.supply;

/**
 * Why a request was rejected before the auction. The caller answers 204 for every reason, the same
 * as a no-bid; the reason shows up only as the {@code exchange.reject} metric tag.
 */
public enum RejectReason {
    // supply (SupplyResolver)
    MISSING_SOURCE,
    BAD_SOURCE_ID,
    UNKNOWN_SOURCE,
    // ORTB validation (RequestValidationService)
    MISSING_REQUEST_ID,
    MISSING_IMP,
    APP_XOR_SITE,
    IMP_MISSING_ID,
    IMP_NO_FORMAT,
    NEGATIVE_BIDFLOOR
}
