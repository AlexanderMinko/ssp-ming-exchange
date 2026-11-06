package com.ming.sspexchange.service.demand;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.BidRequest;

public record OutboundRequest(Bidder bidder, BidRequest request, int timeoutMs) { }
