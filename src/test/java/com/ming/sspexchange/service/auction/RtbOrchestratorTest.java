package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.entity.SupplyPartner;
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
import com.ming.sspexchange.service.demand.FanOutService;
import com.ming.sspexchange.service.event.AuctionEventLogger;
import com.ming.sspexchange.service.mapper.OrtbMapper;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.service.supply.RejectReason;
import com.ming.sspexchange.service.supply.RequestValidator;
import com.ming.sspexchange.service.supply.SupplyResolver;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RtbOrchestratorTest {

    private final SupplyResolver supplyResolver = mock(SupplyResolver.class);
    private final RequestValidator validator = mock(RequestValidator.class);
    private final PartnerConfigCache cache = mock(PartnerConfigCache.class);
    private final BidRequestForwarder forwarder =
            new BidRequestForwarder(Mappers.getMapper(OrtbMapper.class));
    private final FanOutService fanOut = mock(FanOutService.class);
    private final WinnerSelector winnerSelector = new WinnerSelector();
    private final ResponseBuilder responseBuilder =
            new ResponseBuilder(new MacroProcessor(), Mappers.getMapper(OrtbMapper.class));
    private final AuctionEventLogger eventLogger = mock(AuctionEventLogger.class);
    private final ExchangeMetrics metrics = new ExchangeMetrics(new SimpleMeterRegistry());

    private final RtbOrchestrator orchestrator = new RtbOrchestrator(
            supplyResolver, validator, cache, forwarder, fanOut,
            winnerSelector, responseBuilder, eventLogger, metrics);

    private static final Bidder ALPHA = Bidder.builder()
            .id("alpha").name("alpha").seat("seat-alpha").endpoint("http://a").status(PartnerStatus.ACTIVE).build();

    private static BidRequest request() {
        var banner = new Banner();
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var pub = new Publisher(); pub.setId("pub-100");
        var app = new App(); app.setPublisher(pub);
        var r = new BidRequest(); r.setId("req-1"); r.setImp(List.of(imp)); r.setApp(app); r.setTmax(350);
        return r;
    }

    private static final SupplyPartner SOURCE = SupplyPartner.builder().id("s-1").entityType(SupplyPartnerType.SOURCE)
            .sourceId("abc").status(PartnerStatus.ACTIVE).build();
    private static final SupplyPartner PUBLISHER = SupplyPartner.builder().id("p-1").entityType(SupplyPartnerType.PUBLISHER)
            .publisherId("pub-100").status(PartnerStatus.ACTIVE).build();

    // Stub supply resolution the way the real resolver does it: fill RtbData, return no reject.
    private void supplyResolves() {
        when(supplyResolver.resolve(any())).thenAnswer(inv -> {
            RtbContext ctx = inv.getArgument(0);
            ctx.data().setSource(SOURCE);
            ctx.data().setPublisher(PUBLISHER);
            return Optional.empty();
        });
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
        supplyResolves();
        when(cache.bidders()).thenReturn(List.of(ALPHA));
        when(fanOut.fanOut(anyList())).thenReturn(List.of(bidFromAlpha(2.5)));

        var outcome = orchestrator.run("abc", request);

        assertThat(outcome).isInstanceOf(RtbOutcome.Fill.class);
        var response = ((RtbOutcome.Fill) outcome).response();
        assertThat(response.getSeatbid().get(0).getSeat()).isEqualTo("seat-alpha");
        assertThat(response.getSeatbid().get(0).getBid().get(0).getNurl())
                .isEqualTo("http://a/win?p=2.5");
        verify(eventLogger).log(argThat(e -> e.winner().equals("alpha") && e.responseStatus() == 200
                && e.publisherId().equals("pub-100") && e.supplierType().equals("PUBLISHER")));
    }

    @Test
    void noActiveBiddersShortCircuitsWithEvent() {
        var request = request();
        supplyResolves();
        when(cache.bidders()).thenReturn(List.of(Bidder.builder()
                .id("gamma").name("gamma").seat("seat-gamma").endpoint("http://g").status(PartnerStatus.PAUSED).build()));

        assertThat(orchestrator.run("abc", request)).isEqualTo(RtbOutcome.NO_BID);
        verify(fanOut, never()).fanOut(anyList());
        verify(eventLogger).log(argThat(e -> e.responseStatus() == 204));
    }

    @Test
    void noValidBidsIsNoBid() {
        var request = request();
        supplyResolves();
        when(cache.bidders()).thenReturn(List.of(ALPHA));
        when(fanOut.fanOut(anyList())).thenReturn(
                List.of(new BidderCallResult(ALPHA, BidderOutcome.TIMEOUT, null, 300)));

        assertThat(orchestrator.run("abc", request)).isEqualTo(RtbOutcome.NO_BID);
        verify(eventLogger).log(argThat(e -> e.responseStatus() == 204));
    }

    @Test
    void validationFailureIsRejectedWithoutAuction() {
        var request = request();
        supplyResolves();
        when(validator.validate(request)).thenReturn(Optional.of(RejectReason.MISSING_IMP));

        assertThat(orchestrator.run("abc", request)).isEqualTo(new RtbOutcome.Rejected(RejectReason.MISSING_IMP));
        verify(fanOut, never()).fanOut(anyList());
        verify(eventLogger, never()).log(any());
    }

    @Test
    void supplyRejectSkipsValidationAndAuction() {
        var request = request();
        when(supplyResolver.resolve(any())).thenReturn(Optional.of(RejectReason.UNKNOWN_SOURCE));

        assertThat(orchestrator.run("zzz", request)).isEqualTo(new RtbOutcome.Rejected(RejectReason.UNKNOWN_SOURCE));
        verify(validator, never()).validate(any());
        verify(fanOut, never()).fanOut(anyList());
    }
}
