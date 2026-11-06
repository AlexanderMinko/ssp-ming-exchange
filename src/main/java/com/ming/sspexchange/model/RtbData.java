package com.ming.sspexchange.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.service.auction.WinningBid;
import com.ming.sspexchange.service.demand.BidderCallResult;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** Everything the serving flow learns about one request, in the order the stages fill it. */
@Getter
@Setter
@RequiredArgsConstructor
public class RtbData {

    private final long startNanos;
    private final String sourceId;                // raw ?account= value

    // supply (SupplyResolver)
    private SupplyPartner source;
    private SupplyPartner publisher;             // null when the request's publisher is not registered

    // demand + auction (RtbOrchestrator)
    private Set<AdFormat> formats = Set.of();
    private int effectiveTmax;
    private List<Bidder> eligibleBidders = List.of();
    private List<BidderCallResult> bidderResults = List.of();
    private WinningBid winner;

    /** The supplier whose config applies: the registered publisher, else the source. */
    public SupplyPartner getSupplier() {
        return Objects.nonNull(publisher) ? publisher : source;
    }


    public String getPublisherId() {
        return Objects.nonNull(publisher) ? publisher.getPublisherId() : null;
    }
}
