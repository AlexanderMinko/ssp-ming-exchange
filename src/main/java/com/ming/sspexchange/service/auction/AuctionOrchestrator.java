package com.ming.sspexchange.service.auction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.demand.BidRequestForwarder;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.demand.BidderTargetingFilter;
import com.ming.sspexchange.service.demand.FanOutService;
import com.ming.sspexchange.service.demand.TmaxPolicy;
import com.ming.sspexchange.service.event.AuctionEvent;
import com.ming.sspexchange.service.event.AuctionEventLogger;
import com.ming.sspexchange.service.event.BidderOutcomeEvent;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.service.supply.RequestValidator;
import com.ming.sspexchange.service.supply.SupplyContext;
import com.ming.sspexchange.service.supply.SupplyResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionOrchestrator {

    private final SupplyResolver supplyResolver;
    private final RequestValidator validator;
    private final PartnerConfigCache cache;
    private final BidderTargetingFilter targetingFilter;
    private final BidRequestForwarder forwarder;
    private final FanOutService fanOut;
    private final WinnerSelector winnerSelector;
    private final ResponseBuilder responseBuilder;
    private final AuctionEventLogger eventLogger;
    private final ExchangeMetrics metrics;

    public Optional<BidResponse> run(String accountKey, BidRequest request) {
        long start = System.nanoTime();
        var supply = supplyResolver.resolve(accountKey, request);
        validator.validate(request);

        var eligible = targetingFilter.eligible(request, cache.bidders());
        if (eligible.isEmpty()) {
            metrics.noBid("no_eligible_bidders");
            finish(request, supply, List.of(), List.of(), null, 204, start);
            return Optional.empty();
        }

        int effectiveTmax = TmaxPolicy.effective(request.getTmax());
        var outbound = eligible.stream()
                .map(bidder -> forwarder.outbound(request, bidder, effectiveTmax))
                .toList();
        var results = fanOut.fanOut(outbound);
        results.forEach(r -> metrics.bidderResult(r.bidder().getName(), r.outcome(), r.latencyMs()));

        var winner = winnerSelector.select(request, results);
        if (winner.isEmpty()) {
            boolean anyBids = results.stream().anyMatch(r -> r.outcome() == BidderOutcome.BID);
            metrics.noBid(anyBids ? "no_valid_bids" : "no_bids");
            finish(request, supply, eligible, results, null, 204, start);
            return Optional.empty();
        }

        var response = responseBuilder.build(request, winner.get());
        metrics.win(winner.get().bidder().getName());
        finish(request, supply, eligible, results, winner.get(), 200, start);
        return Optional.of(response);
    }

    private void finish(BidRequest request, SupplyContext supply, List<BidderEntity> eligible,
            List<BidderCallResult> results, WinningBid winner, int status, long startNanos) {
        long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
        metrics.auction(totalMs);
        String firstImpId = request.getImp().get(0).getId();
        eventLogger.log(new AuctionEvent(
                request.getId(),
                supply.account().getAccountKey(),
                supply.publisher().getPublisherId(),
                BidderTargetingFilter.requestFormats(request).stream().map(Enum::name).toList(),
                eligible.stream().map(BidderEntity::getName).toList(),
                results.stream().map(r -> new BidderOutcomeEvent(
                        r.bidder().getName(), r.outcome().name(), r.latencyMs(),
                        r.outcome() == BidderOutcome.BID ? bidderPrice(firstImpId, r) : null)).toList(),
                Objects.nonNull(winner) ? winner.bidder().getName() : null,
                Objects.nonNull(winner) ? winner.bid().getPrice() : null,
                status,
                totalMs));
    }

    // Best price this bidder offered on the imp that was actually auctioned (first imp, v1 scope),
    // so the logged price matches what competed rather than an unrelated imp's higher bid.
    private static Double bidderPrice(String firstImpId, BidderCallResult result) {
        return result.response().getSeatbid().stream()
                .filter(sb -> Objects.nonNull(sb.getBid()))
                .flatMap(sb -> sb.getBid().stream())
                .filter(b -> firstImpId.equals(b.getImpid()))
                .map(Bid::getPrice)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
    }
}
