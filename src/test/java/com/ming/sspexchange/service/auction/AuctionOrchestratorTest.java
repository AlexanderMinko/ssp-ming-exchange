package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.PublisherEntity;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.Banner;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.demand.BidRequestForwarder;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.demand.BidderTargetingFilter;
import com.ming.sspexchange.service.demand.FanOutService;
import com.ming.sspexchange.service.event.AuctionEventLogger;
import com.ming.sspexchange.service.mapper.OrtbMapper;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.service.supply.InvalidBidRequestException;
import com.ming.sspexchange.service.supply.RequestValidator;
import com.ming.sspexchange.service.supply.SupplyContext;
import com.ming.sspexchange.service.supply.SupplyResolver;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AuctionOrchestratorTest {

    private final SupplyResolver supplyResolver = mock(SupplyResolver.class);
    private final RequestValidator validator = mock(RequestValidator.class);
    private final PartnerConfigCache cache = mock(PartnerConfigCache.class);
    private final BidderTargetingFilter targetingFilter = mock(BidderTargetingFilter.class);
    private final BidRequestForwarder forwarder =
            new BidRequestForwarder(Mappers.getMapper(OrtbMapper.class));
    private final FanOutService fanOut = mock(FanOutService.class);
    private final WinnerSelector winnerSelector = new WinnerSelector();
    private final ResponseBuilder responseBuilder =
            new ResponseBuilder(new MacroProcessor(), Mappers.getMapper(OrtbMapper.class));
    private final AuctionEventLogger eventLogger = mock(AuctionEventLogger.class);
    private final ExchangeMetrics metrics = new ExchangeMetrics(new SimpleMeterRegistry());

    private final AuctionOrchestrator orchestrator = new AuctionOrchestrator(
            supplyResolver, validator, cache, targetingFilter, forwarder, fanOut,
            winnerSelector, responseBuilder, eventLogger, metrics);

    private static final BidderEntity ALPHA = BidderEntity.builder()
            .id("alpha").name("alpha").seat("seat-alpha").endpoint("http://a").build();

    private static BidRequest request() {
        var banner = new Banner();
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var pub = new Publisher(); pub.setId("pub-100");
        var app = new App(); app.setPublisher(pub);
        var r = new BidRequest(); r.setId("req-1"); r.setImp(List.of(imp)); r.setApp(app); r.setTmax(350);
        return r;
    }

    private static SupplyContext supply() {
        return new SupplyContext(
                AccountEntity.builder().id("acc-1").accountKey("key-1").status(PartnerStatus.ACTIVE).build(),
                PublisherEntity.builder().id("p-1").accountId("acc-1").publisherId("pub-100")
                        .status(PartnerStatus.ACTIVE).build());
    }

    private static BidderCallResult bidFromAlpha(double price) {
        var bid = new Bid(); bid.setId("b1"); bid.setImpid("imp-1"); bid.setPrice(price);
        bid.setAdm("<div/>"); bid.setNurl("http://a/win?p=${AUCTION_PRICE}");
        var sb = new SeatBid(); sb.setSeat("seat-alpha"); sb.setBid(List.of(bid));
        var resp = new BidResponse(); resp.setId("req-1"); resp.setSeatbid(List.of(sb));
        return new BidderCallResult(ALPHA, BidderOutcome.BID, resp, 40);
    }

    @Test
    void fillPathReturnsResponseWithSubstitutedNurlAndLogsEvent() {
        var request = request();
        when(supplyResolver.resolve("key-1", request)).thenReturn(supply());
        when(cache.bidders()).thenReturn(List.of(ALPHA));
        when(targetingFilter.eligible(request, List.of(ALPHA))).thenReturn(List.of(ALPHA));
        when(fanOut.fanOut(anyList())).thenReturn(List.of(bidFromAlpha(2.5)));

        var response = orchestrator.run("key-1", request);

        assertThat(response).isPresent();
        assertThat(response.get().getSeatbid().get(0).getSeat()).isEqualTo("seat-alpha");
        assertThat(response.get().getSeatbid().get(0).getBid().get(0).getNurl())
                .isEqualTo("http://a/win?p=2.5");
        verify(eventLogger).log(argThat(e -> e.winner().equals("alpha") && e.responseStatus() == 200));
    }

    @Test
    void noEligibleBiddersShortCircuitsWithEvent() {
        var request = request();
        when(supplyResolver.resolve("key-1", request)).thenReturn(supply());
        when(cache.bidders()).thenReturn(List.of(ALPHA));
        when(targetingFilter.eligible(request, List.of(ALPHA))).thenReturn(List.of());

        assertThat(orchestrator.run("key-1", request)).isEmpty();
        verify(fanOut, never()).fanOut(anyList());
        verify(eventLogger).log(argThat(e -> e.responseStatus() == 204));
    }

    @Test
    void noValidBidsIsNoBid() {
        var request = request();
        when(supplyResolver.resolve("key-1", request)).thenReturn(supply());
        when(cache.bidders()).thenReturn(List.of(ALPHA));
        when(targetingFilter.eligible(request, List.of(ALPHA))).thenReturn(List.of(ALPHA));
        when(fanOut.fanOut(anyList())).thenReturn(
                List.of(new BidderCallResult(ALPHA, BidderOutcome.TIMEOUT, null, 300)));

        assertThat(orchestrator.run("key-1", request)).isEmpty();
        verify(eventLogger).log(argThat(e -> e.responseStatus() == 204));
    }

    @Test
    void validationFailurePropagates() {
        var request = request();
        when(supplyResolver.resolve("key-1", request)).thenReturn(supply());
        doThrow(new InvalidBidRequestException("bad")).when(validator).validate(request);

        assertThatThrownBy(() -> orchestrator.run("key-1", request))
                .isInstanceOf(InvalidBidRequestException.class);
    }
}
