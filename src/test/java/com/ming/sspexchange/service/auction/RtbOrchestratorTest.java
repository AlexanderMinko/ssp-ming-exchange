package com.ming.sspexchange.service.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.LoggerFactory;

import com.ming.sspexchange.cache.BidderCache;
import com.ming.sspexchange.model.RtbContext;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.model.openrtb.App;
import com.ming.sspexchange.model.openrtb.Banner;
import com.ming.sspexchange.model.openrtb.Bid;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.model.openrtb.Publisher;
import com.ming.sspexchange.model.openrtb.SeatBid;
import com.ming.sspexchange.service.RtbOrchestrator;
import com.ming.sspexchange.service.demand.BidderCallResult;
import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.demand.BidderService;
import com.ming.sspexchange.logging.LogField;
import com.ming.sspexchange.service.event.AuctionEvent;
import com.ming.sspexchange.service.mapper.AuctionEventMapper;
import com.ming.sspexchange.service.mapper.BidResponseMapper;
import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.service.supply.RejectReason;
import com.ming.sspexchange.service.supply.RequestValidationService;
import com.ming.sspexchange.service.supply.SupplyResolver;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RtbOrchestratorTest {

    private final SupplyResolver supplyResolver = mock(SupplyResolver.class);
    private final RequestValidationService validator = mock(RequestValidationService.class);
    private final BidderCache bidderCache = mock(BidderCache.class);
    private final BidderService bidderService = mock(BidderService.class);
    private final AuctionService auctionService = new AuctionService(new BidResponseValidationService());
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ExchangeMetrics metrics = new ExchangeMetrics(registry);

    private final RtbOrchestrator orchestrator = new RtbOrchestrator(
            supplyResolver, validator, bidderCache, bidderService, auctionService,
            Mappers.getMapper(BidResponseMapper.class),
            Mappers.getMapper(AuctionEventMapper.class), metrics);

    private final Logger orchestratorLog = (Logger) LoggerFactory.getLogger(RtbOrchestrator.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        orchestratorLog.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        orchestratorLog.detachAppender(logged);
    }

    private List<ILoggingEvent> lines(String message) {
        return logged.list.stream().filter(line -> message.equals(line.getMessage())).toList();
    }

    /** The AuctionEvent in the one "auction" line. */
    private AuctionEvent auctionEvent() {
        assertThat(lines("auction")).hasSize(1);
        return (AuctionEvent) field(lines("auction").get(0), "auction");
    }

    private static Object field(ILoggingEvent line, String key) {
        return Arrays.stream(line.getArgumentArray())
                .map(LogField.class::cast)
                .filter(logField -> logField.key().equals(key))
                .findFirst().orElseThrow().resolvedValue();
    }

    private static final Bidder ALPHA = Bidder.builder()
            .id("alpha").name("alpha").seat("seat-alpha").endpoint("/bid/alpha").build();

    private static BidRequest request() {
        var banner = new Banner();
        var imp = new Imp(); imp.setId("imp-1"); imp.setBanner(banner);
        var pub = new Publisher(); pub.setId("pub-100");
        var app = new App(); app.setPublisher(pub);
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp)); request.setApp(app); request.setTmax(350);
        return request;
    }

    private static final SupplyPartner SOURCE = SupplyPartner.builder().id("s-1").entityType(SupplyPartnerType.SOURCE)
            .sourceId("abc").build();
    private static final SupplyPartner PUBLISHER = SupplyPartner.builder().id("p-1").entityType(SupplyPartnerType.PUBLISHER)
            .publisherId("pub-100").build();

    // Stub supply resolution the way the real resolver does it: fill the context, return no reject.
    private void supplyResolves() {
        when(supplyResolver.resolve(any())).thenAnswer(invocation -> {
            RtbContext ctx = invocation.getArgument(0);
            ctx.data().setSource(SOURCE);
            ctx.data().setPublisher(PUBLISHER);
            return Optional.empty();
        });
    }

    private static BidderCallResult bidFromAlpha(double price) {
        var bid = new Bid(); bid.setId("b1"); bid.setImpid("imp-1"); bid.setPrice(price);
        bid.setAdm("<div/>"); bid.setNurl("http://a/win?p=${AUCTION_PRICE}");
        var seatBid = new SeatBid(); seatBid.setSeat("seat-alpha"); seatBid.setBid(List.of(bid));
        var response = new BidResponse(); response.setId("req-1"); response.setSeatbid(List.of(seatBid));
        return new BidderCallResult(ALPHA, BidderOutcome.BID, response, 40);
    }

    @Test
    void fillPathReturnsResponseWithSubstitutedNurlAndLogsEvent() {
        var request = request();
        supplyResolves();
        when(bidderCache.all()).thenReturn(List.of(ALPHA));
        when(bidderService.fanOut(any(), anyList())).thenReturn(List.of(bidFromAlpha(2.5)));

        var response = orchestrator.run("abc", request).orElseThrow();
        assertThat(response.getSeatbid().get(0).getSeat()).isEqualTo("seat-alpha");
        assertThat(response.getSeatbid().get(0).getBid().get(0).getNurl())
                .isEqualTo("http://a/win?p=2.5");
        var event = auctionEvent();
        assertThat(event.winner()).isEqualTo("alpha");
        assertThat(event.responseStatus()).isEqualTo(200);
        assertThat(event.publisherId()).isEqualTo("pub-100");
        assertThat(event.supplierType()).isEqualTo("PUBLISHER");
        assertThat(event.bidders()).containsExactly(new AuctionEvent.BidderResult("alpha", "BID", 40, 2.5));
        assertThat(lines("received adRequest")).singleElement().satisfies(line -> {
            assertThat(field(line, "adRequest")).isSameAs(request);
            assertThat(field(line, "sourceId")).isEqualTo("abc");
        });
        assertThat(registry.counter("exchange.wins", "bidder", "alpha").count()).isEqualTo(1.0);
    }

    @Test
    void noConfiguredBiddersShortCircuitsWithEvent() {
        var request = request();
        supplyResolves();
        when(bidderCache.all()).thenReturn(List.of());

        assertThat(orchestrator.run("abc", request)).isEmpty();
        verify(bidderService, never()).fanOut(any(), anyList());
        assertThat(auctionEvent().responseStatus()).isEqualTo(204);
        assertThat(auctionEvent().bidders()).isEmpty();
        assertThat(registry.counter("exchange.nobid", "reason", "no_bidders_configured").count()).isEqualTo(1.0);
    }

    @Test
    void noValidBidsIsNoBid() {
        var request = request();
        supplyResolves();
        when(bidderCache.all()).thenReturn(List.of(ALPHA));
        when(bidderService.fanOut(any(), anyList())).thenReturn(
                List.of(new BidderCallResult(ALPHA, BidderOutcome.TIMEOUT, null, 300)));

        assertThat(orchestrator.run("abc", request)).isEmpty();
        assertThat(auctionEvent().responseStatus()).isEqualTo(204);
        assertThat(auctionEvent().bidders()).containsExactly(new AuctionEvent.BidderResult("alpha", "TIMEOUT", 300, null));
        assertThat(registry.counter("exchange.nobid", "reason", "no_bids").count()).isEqualTo(1.0);
    }

    @Test
    void bidUnderFloorIsNoValidBids() {
        var request = request();
        request.getImp().get(0).setBidfloor(5.0);
        supplyResolves();
        when(bidderCache.all()).thenReturn(List.of(ALPHA));
        when(bidderService.fanOut(any(), anyList())).thenReturn(List.of(bidFromAlpha(2.5)));

        assertThat(orchestrator.run("abc", request)).isEmpty();
        assertThat(registry.counter("exchange.nobid", "reason", "no_valid_bids").count()).isEqualTo(1.0);
    }

    @Test
    void validationFailureIsRejectedWithoutAuction() {
        var request = request();
        supplyResolves();
        when(validator.validate(request)).thenReturn(Optional.of(RejectReason.MISSING_IMP));

        assertThat(orchestrator.run("abc", request)).isEmpty();
        assertThat(registry.counter("exchange.reject", "reason", RejectReason.MISSING_IMP.name()).count()).isEqualTo(1.0);
        verify(bidderService, never()).fanOut(any(), anyList());
        assertThat(lines("auction")).isEmpty();
        assertThat(lines("received adRequest")).hasSize(1);   // logged before any reject
    }

    @Test
    void supplyRejectSkipsValidationAndAuction() {
        var request = request();
        when(supplyResolver.resolve(any())).thenReturn(Optional.of(RejectReason.UNKNOWN_SOURCE));

        assertThat(orchestrator.run("zzz", request)).isEmpty();
        assertThat(registry.counter("exchange.reject", "reason", RejectReason.UNKNOWN_SOURCE.name()).count()).isEqualTo(1.0);
        verify(validator, never()).validate(any());
        verify(bidderService, never()).fanOut(any(), anyList());
    }
}
