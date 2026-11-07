package com.ming.sspexchange.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.BidderCache;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.RtbData;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.auction.AuctionService;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.demand.BidderService;
import com.ming.sspexchange.service.event.AuctionEventLogger;
import com.ming.sspexchange.service.mapper.AuctionEventMapper;
import com.ming.sspexchange.service.mapper.BidResponseMapper;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.service.supply.RequestValidationService;
import com.ming.sspexchange.service.supply.SupplyResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The serving flow, top to bottom: supply → validation → demand fan-out → auction → response.
 * Each stage reads from and writes into the request's {@link RtbData}, carried by the {@link RtbContext}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RtbOrchestrator {

    private final SupplyResolver supplyResolver;
    private final RequestValidationService requestValidator;
    private final BidderCache bidderCache;
    private final BidderService bidderService;
    private final AuctionService auctionService;
    private final BidResponseMapper bidResponseMapper;
    private final AuctionEventMapper eventMapper;
    private final AuctionEventLogger eventLogger;
    private final ExchangeMetrics metrics;

    /** The bid response on a fill. Empty on a no-bid or a reject: the caller answers 204 either way. */
    public Optional<BidResponse> run(String sourceId, BidRequest request) {
        var data = new RtbData();
        data.setStartNanos(System.nanoTime());
        data.setSourceId(sourceId);
        var ctx = new RtbContext(request, data);

        // 1. Admission: supply first, then ORTB validation. The first reject wins and no auction runs.
        var reject = supplyResolver.resolve(ctx)
                .or(() -> requestValidator.validate(request));
        if (reject.isPresent()) {
            log.debug("reject {}", reject.get());
            metrics.reject(reject.get());
            return Optional.empty();
        }

        // 2. Demand: no targeting yet, every configured bidder is called.
        var bidders = bidderCache.all();
        if (bidders.isEmpty()) {
            return noBid(ctx, "no_bidders_configured");
        }
        data.setBidderResults(bidderService.fanOut(request, bidders));
        data.getBidderResults().forEach(result ->
                metrics.bidderResult(result.bidder().getName(), result.outcome(), result.latencyMs()));

        // 3. Auction.
        var winner = auctionService.selectWinner(request, data.getBidderResults());
        if (winner.isEmpty()) {
            boolean anyBids = data.getBidderResults().stream()
                    .anyMatch(result -> result.outcome() == BidderOutcome.BID);
            return noBid(ctx, anyBids ? "no_valid_bids" : "no_bids");
        }
        data.setWinner(winner.get());

        // 4. Response.
        var response = bidResponseMapper.toBidResponse(request, data.getWinner());
        metrics.win(data.getWinner().bidder().getName());
        finish(ctx, 200);
        return Optional.of(response);
    }

    private Optional<BidResponse> noBid(RtbContext ctx, String reason) {
        metrics.noBid(reason);
        finish(ctx, 204);
        return Optional.empty();
    }

    // Rejects never reach here: AUCTION_EVENTS only holds auctions that ran.
    private void finish(RtbContext ctx, int responseStatus) {
        long totalMs = (System.nanoTime() - ctx.data().getStartNanos()) / 1_000_000;
        metrics.auction(totalMs);
        eventLogger.log(eventMapper.toEvent(ctx, responseStatus, totalMs));
    }
}
