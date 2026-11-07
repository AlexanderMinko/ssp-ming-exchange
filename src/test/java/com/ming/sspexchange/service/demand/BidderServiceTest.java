package com.ming.sspexchange.service.demand;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ming.sspexchange.config.BidderClientConfig;
import com.ming.sspexchange.config.BidderClientProperties;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;
import com.ming.sspexchange.service.mapper.OrtbMapper;

import io.micrometer.observation.ObservationRegistry;

/** The tmax policy (pure), one bidder call (WireMock) and the concurrent fan-out (WireMock). */
class BidderServiceTest {

    static WireMockServer wm = new WireMockServer(0);
    static BidderService bidderService;

    @BeforeAll
    static void setUp() {
        wm.start();
        var dslJson = new DslJson<>(Settings.withRuntime()
                .allowArrayFormat(true).skipDefaultValues(true).includeServiceLoader());
        var props = new BidderClientProperties(wm.baseUrl(), 100, 800);
        var config = new BidderClientConfig();
        var httpClient = config.bidderHttpClient(props);
        var restClient = config.bidderRestClient(dslJson, httpClient, props, ObservationRegistry.NOOP);
        bidderService = new BidderService(restClient, Mappers.getMapper(OrtbMapper.class),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterAll
    static void tearDown() { wm.stop(); }

    private static Bidder bidder(String path) {
        return Bidder.builder().id(path).name(path).seat("s").endpoint(path).build();
    }

    private static BidRequest request() {
        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp)); request.setTmax(350);
        return request;
    }

    @Test void effectiveTmaxDefaultsWhenAbsent()   { assertThat(BidderService.effectiveTmax(null)).isEqualTo(300); }
    @Test void effectiveTmaxClampsLow()            { assertThat(BidderService.effectiveTmax(10)).isEqualTo(100); }
    @Test void effectiveTmaxClampsHigh()           { assertThat(BidderService.effectiveTmax(5000)).isEqualTo(1000); }
    @Test void effectiveTmaxPassesThroughInRange() { assertThat(BidderService.effectiveTmax(350)).isEqualTo(350); }

    @Test void outboundSubtractsOverhead()     { assertThat(BidderService.outboundTimeout(350, null)).isEqualTo(300); }
    @Test void lowerBidderOverrideWins()       { assertThat(BidderService.outboundTimeout(350, 200)).isEqualTo(200); }
    @Test void higherBidderOverrideIsIgnored() { assertThat(BidderService.outboundTimeout(350, 900)).isEqualTo(300); }

    @Test
    void bidResponseYieldsBidOutcome() {
        wm.stubFor(post("/bid").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"s","bid":[{"id":"b1","impid":"imp-1","price":1.5,"adm":"<div/>"}]}]}
            """)));

        var result = bidderService.call(bidder("/bid"), request());

        assertThat(result.outcome()).isEqualTo(BidderOutcome.BID);
        assertThat(result.response().getSeatbid().get(0).getBid().get(0).getPrice()).isEqualTo(1.5);
        assertThat(result.bestPrice("imp-1")).hasValue(1.5);
        assertThat(result.bestPrice("imp-9")).isEmpty();
    }

    @Test
    void http204YieldsNoBid() {
        wm.stubFor(post("/nobid").willReturn(aResponse().withStatus(204)));
        assertThat(bidderService.call(bidder("/nobid"), request()).outcome()).isEqualTo(BidderOutcome.NO_BID);
    }

    @Test
    void emptySeatbidYieldsNoBid() {
        wm.stubFor(post("/empty").willReturn(okJson("{\"id\":\"req-1\",\"seatbid\":[]}")));
        assertThat(bidderService.call(bidder("/empty"), request()).outcome()).isEqualTo(BidderOutcome.NO_BID);
    }

    @Test
    void http500YieldsError() {
        wm.stubFor(post("/boom").willReturn(aResponse().withStatus(500)));
        assertThat(bidderService.call(bidder("/boom"), request()).outcome()).isEqualTo(BidderOutcome.ERROR);
    }

    @Test
    void malformedBodyYieldsError() {
        wm.stubFor(post("/garbage").willReturn(okJson("{not json")));
        assertThat(bidderService.call(bidder("/garbage"), request()).outcome()).isEqualTo(BidderOutcome.ERROR);
    }

    @Test
    void fanOutIsolatesSlowBidderAsTimeoutAndSendsPerBidderTmax() {
        wm.stubFor(post("/fast").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"s","bid":[{"id":"b1","impid":"imp-1","price":2.0,"adm":"<div/>"}]}]}
            """)));
        wm.stubFor(post("/slow").willReturn(aResponse().withStatus(204).withFixedDelay(2000)));

        // request tmax 350 → 300 ms outbound deadline per bidder (overhead 50 ms).
        var results = bidderService.fanOut(request(), List.of(bidder("/fast"), bidder("/slow")));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).outcome()).isEqualTo(BidderOutcome.BID);
        assertThat(results.get(1).outcome()).isEqualTo(BidderOutcome.TIMEOUT);
        assertThat(results.get(1).latencyMs()).isEqualTo(300);
        assertThat(wm.getAllServeEvents()).anySatisfy(event ->
                assertThat(event.getRequest().getBodyAsString()).contains("\"tmax\":300"));
    }
}
