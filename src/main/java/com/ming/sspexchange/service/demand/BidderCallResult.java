package com.ming.sspexchange.service.demand;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.BidResponse;

public record BidderCallResult(BidderEntity bidder, BidderOutcome outcome, BidResponse response, long latencyMs) { }
