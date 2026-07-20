package com.ming.sspexchange.service.demand;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.BidRequest;

public record OutboundRequest(BidderEntity bidder, BidRequest request, int timeoutMs) { }
