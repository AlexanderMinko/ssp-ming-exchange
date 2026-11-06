package com.ming.sspexchange.service.demand;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.BidResponse;

public record BidderCallResult(Bidder bidder, BidderOutcome outcome, BidResponse response, long latencyMs) { }
