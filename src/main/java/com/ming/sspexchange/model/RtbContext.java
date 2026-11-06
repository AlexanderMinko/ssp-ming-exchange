package com.ming.sspexchange.model;

import com.ming.sspexchange.model.openrtb.BidRequest;

/**
 * One per incoming request: the ad request as received plus the {@link RtbData} each serving stage
 * fills in. Passed through the whole flow instead of growing method signatures.
 */
public record RtbContext(BidRequest adRequest, RtbData data) {

    public static RtbContext start(BidRequest adRequest, String sourceId) {
        return new RtbContext(adRequest, new RtbData(System.nanoTime(), sourceId));
    }
}
