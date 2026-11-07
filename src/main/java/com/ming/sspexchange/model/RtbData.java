package com.ming.sspexchange.model;

import java.util.List;

import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.service.auction.WinningBid;
import com.ming.sspexchange.service.demand.BidderCallResult;

import lombok.Getter;
import lombok.Setter;

/**
 * What one stage learns and a later stage needs, in the order the stages fill it. Plain data, no logic.
 * Anything a stage can fetch itself (the bidder list, the config caches) stays out.
 */
@Getter
@Setter
public class RtbData {

    // request intake (RtbOrchestrator)
    private long startNanos;
    private String sourceId;                      // raw ?account= value

    // supply (SupplyResolver)
    private SupplyPartner source;
    private SupplyPartner publisher;              // null when the request's publisher is not registered

    // demand + auction (RtbOrchestrator)
    private List<BidderCallResult> bidderResults = List.of();
    private WinningBid winner;                    // null until a bid wins
}
