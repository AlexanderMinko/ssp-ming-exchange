package com.ming.sspexchange.service.demand;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.service.mapper.OrtbMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BidRequestForwarder {

    private final OrtbMapper ortbMapper;

    public OutboundRequest outbound(BidRequest in, BidderEntity bidder, int effectiveTmax) {
        int timeoutMs = TmaxPolicy.outbound(effectiveTmax, bidder.getTmaxMs());
        return new OutboundRequest(bidder, ortbMapper.copyWithTmax(in, timeoutMs), timeoutMs);
    }
}
