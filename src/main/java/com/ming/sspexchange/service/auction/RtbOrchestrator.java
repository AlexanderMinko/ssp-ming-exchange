package com.ming.sspexchange.service.auction;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.service.demand.BidRequestForwarder;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.demand.FanOutService;
import com.ming.sspexchange.service.demand.TmaxPolicy;
import com.ming.sspexchange.service.event.AuctionEvent;
import com.ming.sspexchange.service.event.AuctionEventLogger;
import com.ming.sspexchange.service.event.BidderOutcomeEvent;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.service.supply.RejectReason;
import com.ming.sspexchange.service.supply.RequestValidator;
import com.ming.sspexchange.service.supply.SupplyResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RtbOrchestrator {

    private final SupplyResolver supplyResolver;
    private final RequestValidator validator;
    private final PartnerConfigCache cache;
    private final BidRequestForwarder forwarder;
    private final FanOutService fanOut;
    private final WinnerSelector winnerSelector;
    private final ResponseBuilder responseBuilder;
    private final AuctionEventLogger eventLogger;
    private final ExchangeMetrics metrics;

    public RtbOutcome run(String sourceId, BidRequest adRequest) {
        var ctx = RtbContext.start(adRequest, sourceId);
        // Supply first, then ORTB validation; the first reject wins and the auction never runs.
        return supplyResolver.resolve(ctx)
                .or(() -> validator.validate(adRequest))
                .map(this::reject)
                .orElseGet(() -> auction(ctx));
    }

    // Rejects are counted but kept out of AUCTION_EVENTS — no auction ran.
    private RtbOutcome reject(RejectReason reason) {
        log.debug("reject {}: {}", reason.getHttpStatus(), reason);
        metrics.reject(reason);
        return new RtbOutcome.Rejected(reason);
    }

    private RtbOutcome auction(RtbContext ctx) {
        var request = ctx.adRequest();
        var data = ctx.data();
        data.setFormats(requestFormats(request));

        // No targeting for now: every ACTIVE bidder is called. Status stays as the on/off switch.
        data.setEligibleBidders(cache.bidders().stream()
                .filter(b -> b.getStatus() == PartnerStatus.ACTIVE)
                .toList());
        if (data.getEligibleBidders().isEmpty()) {
            metrics.noBid("no_eligible_bidders");
            finish(ctx, 204);
            return RtbOutcome.NO_BID;
        }

        data.setEffectiveTmax(TmaxPolicy.effective(request.getTmax()));
        var outbound = data.getEligibleBidders().stream()
                .map(bidder -> forwarder.outbound(request, bidder, data.getEffectiveTmax()))
                .toList();
        data.setBidderResults(fanOut.fanOut(outbound));
        data.getBidderResults().forEach(r -> metrics.bidderResult(r.bidder().getName(), r.outcome(), r.latencyMs()));

        data.setWinner(winnerSelector.select(request, data.getBidderResults()).orElse(null));
        if (Objects.isNull(data.getWinner())) {
            boolean anyBids = data.getBidderResults().stream().anyMatch(r -> r.outcome() == BidderOutcome.BID);
            metrics.noBid(anyBids ? "no_valid_bids" : "no_bids");
            finish(ctx, 204);
            return RtbOutcome.NO_BID;
        }

        var response = responseBuilder.build(request, data.getWinner());
        metrics.win(data.getWinner().bidder().getName());
        finish(ctx, 200);
        return new RtbOutcome.Fill(response);
    }

    private void finish(RtbContext ctx, int status) {
        var data = ctx.data();
        long totalMs = (System.nanoTime() - data.getStartNanos()) / 1_000_000;
        metrics.auction(totalMs);
        String firstImpId = ctx.adRequest().getImp().get(0).getId();
        var winner = data.getWinner();
        eventLogger.log(new AuctionEvent(
                ctx.adRequest().getId(),
                data.getSourceId(),
                data.getPublisherId(),
                data.getSupplier().getEntityType().name(),
                data.getFormats().stream().map(Enum::name).toList(),
                data.getEligibleBidders().stream().map(Bidder::getName).toList(),
                data.getBidderResults().stream().map(r -> new BidderOutcomeEvent(
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

    private static Set<AdFormat> requestFormats(BidRequest r) {
        return r.getImp().stream()
                .flatMap(imp -> Stream.of(
                        Objects.nonNull(imp.getBanner()) ? AdFormat.BANNER : null,
                        Objects.nonNull(imp.getVideo()) ? AdFormat.VIDEO : null))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AdFormat.class)));
    }
}
