package com.ming.sspexchange.service.event;

import java.util.List;

import com.dslplatform.json.CompiledJson;

@CompiledJson
public record AuctionEvent(
        String auctionId,
        String accountKey,
        String publisherId,
        List<String> formats,
        List<String> eligibleBidders,
        List<BidderOutcomeEvent> bidders,
        String winner,
        Double clearingPrice,
        int responseStatus,
        long totalLatencyMs) { }
