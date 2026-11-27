package com.ming.sspexchange.service.event;

import java.util.List;

import com.dslplatform.json.CompiledJson;

/**
 * One auction that ran (rejects never get here), every called bidder included. Built by {@code AuctionEventMapper},
 * logged by {@code RtbOrchestrator} as {@code kv("auction", event)} on one INFO line: payload.auction.*, the bidder
 * list as payload.auction.bidders.N.* once the collector flattens it.
 */
@CompiledJson
public record AuctionEvent(
        String auctionId,
        String sourceId,
        String publisherId,       // null when the source config applied
        String supplierType,     // PUBLISHER | SOURCE — whose config applied
        List<String> formats,
        List<BidderResult> bidders,   // one per called bidder, in call order
        String winner,
        Double clearingPrice,
        int responseStatus,
        long totalLatencyMs) {

    /** One called bidder: the auction's verdict, its latency, and its best price on the auctioned imp (BID only). */
    @CompiledJson
    public record BidderResult(String name, String outcome, long latencyMs, Double price) { }
}
