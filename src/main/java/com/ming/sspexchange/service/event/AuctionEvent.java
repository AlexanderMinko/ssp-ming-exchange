package com.ming.sspexchange.service.event;

import java.util.List;

import com.dslplatform.json.CompiledJson;

/** One structured log line per auction that ran (rejects never get here). Built by {@code AuctionEventMapper}. */
@CompiledJson
public record AuctionEvent(
        String auctionId,
        String sourceId,
        String publisherId,       // null when the source config applied
        String supplierType,     // PUBLISHER | SOURCE — whose config applied
        List<String> formats,
        List<String> eligibleBidders,
        List<BidderOutcomeEvent> bidders,
        String winner,
        Double clearingPrice,
        int responseStatus,
        long totalLatencyMs) { }
