package com.ming.sspexchange.service.mapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.RtbData;
import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.event.AuctionEvent;
import com.ming.sspexchange.service.event.BidderOutcomeEvent;

/**
 * {@link RtbContext} → {@link AuctionEvent}, the structured log record of one auction. Nested source paths
 * ({@code ctx.data.winner.bid.price}) are null-safe: a no-bid auction logs a null winner and price.
 * Unmapped targets fail the build.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuctionEventMapper {

    @Mapping(target = "auctionId", source = "ctx.adRequest.id")
    @Mapping(target = "sourceId", source = "ctx.data.sourceId")
    @Mapping(target = "publisherId", source = "ctx.data.publisher.publisherId")
    @Mapping(target = "supplierType", expression = "java(supplierType(ctx.data()))")
    @Mapping(target = "formats", expression = "java(formats(ctx.adRequest()))")
    @Mapping(target = "eligibleBidders", expression = "java(bidderNames(ctx.data()))")
    @Mapping(target = "bidders", expression = "java(bidderEvents(ctx))")
    @Mapping(target = "winner", source = "ctx.data.winner.bidder.name")
    @Mapping(target = "clearingPrice", source = "ctx.data.winner.bid.price")
    @Mapping(target = "responseStatus", source = "responseStatus")
    @Mapping(target = "totalLatencyMs", source = "totalLatencyMs")
    AuctionEvent toEvent(RtbContext ctx, int responseStatus, long totalLatencyMs);

    /** Whose config applied: the registered publisher, else the source. */
    default String supplierType(RtbData data) {
        var supplier = Objects.nonNull(data.getPublisher()) ? data.getPublisher() : data.getSource();
        return supplier.getEntityType().name();
    }

    /** Ad formats offered by the request's imps. */
    default List<String> formats(BidRequest request) {
        return request.getImp().stream()
                .flatMap(imp -> Stream.of(
                        Objects.nonNull(imp.getBanner()) ? AdFormat.BANNER : null,
                        Objects.nonNull(imp.getVideo()) ? AdFormat.VIDEO : null))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AdFormat.class)))
                .stream().map(Enum::name).toList();
    }

    /** Every bidder that was called: one call result per bidder, in call order. */
    default List<String> bidderNames(RtbData data) {
        return data.getBidderResults().stream().map(result -> result.bidder().getName()).toList();
    }

    default List<BidderOutcomeEvent> bidderEvents(RtbContext ctx) {
        String firstImpId = ctx.adRequest().getImp().get(0).getId();
        return ctx.data().getBidderResults().stream()
                .map(result -> toBidderEvent(result, firstImpId))
                .toList();
    }

    // The price logged is the best one on the imp that was auctioned, not an unrelated imp's higher bid.
    default BidderOutcomeEvent toBidderEvent(BidderCallResult result, String firstImpId) {
        var bestPrice = result.bestPrice(firstImpId);
        Double price = result.outcome() == BidderOutcome.BID && bestPrice.isPresent() ? bestPrice.getAsDouble() : null;
        return new BidderOutcomeEvent(result.bidder().getName(), result.outcome().name(), result.latencyMs(), price);
    }
}
