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

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ming.sspexchange.config.BidderClientConfig;
import com.ming.sspexchange.config.BidderClientProperties;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.MockConfig;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.Imp;

import io.micrometer.observation.ObservationRegistry;

class BidderFanOutWireMockTest {

    static WireMockServer wm = new WireMockServer(0);
    static BidderClient client;
    static FanOutService fanOut;

    @BeforeAll
    static void setUp() {
        wm.start();
        var dslJson = new DslJson<>(Settings.withRuntime()
                .allowArrayFormat(true).skipDefaultValues(true).includeServiceLoader());
        var props = new BidderClientProperties(100, 800);
        var config = new BidderClientConfig();
        var httpClient = config.bidderHttpClient(props);
        var restClient = config.bidderRestClient(dslJson, httpClient, props, ObservationRegistry.NOOP);
        client = new BidderClient(restClient);
        fanOut = new FanOutService(client, new MockBidderClient(), Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterAll
    static void tearDown() { wm.stop(); }

    private static OutboundRequest out(String path, int timeoutMs) {
        var bidder = BidderEntity.builder().id(path).name(path).seat("s")
                .endpoint(wm.baseUrl() + path).build();
        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));
        return new OutboundRequest(bidder, request, timeoutMs);
    }

    private static OutboundRequest mockOut(String name, MockConfig mock, int timeoutMs) {
        var bidder = BidderEntity.builder().id(name).name(name).seat("seat-" + name)
                .endpoint("mock:" + name).mock(mock).build();
        var imp = new Imp(); imp.setId("imp-1");
        var request = new BidRequest(); request.setId("req-1"); request.setImp(List.of(imp));
        return new OutboundRequest(bidder, request, timeoutMs);
    }

    @Test
    void bidResponseYieldsBidOutcome() {
        wm.stubFor(post("/bid").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"s","bid":[{"id":"b1","impid":"imp-1","price":1.5,"adm":"<div/>"}]}]}
            """)));

        var result = client.call(out("/bid", 300));

        assertThat(result.outcome()).isEqualTo(BidderOutcome.BID);
        assertThat(result.response().getSeatbid().get(0).getBid().get(0).getPrice()).isEqualTo(1.5);
    }

    @Test
    void http204YieldsNoBid() {
        wm.stubFor(post("/nobid").willReturn(aResponse().withStatus(204)));
        assertThat(client.call(out("/nobid", 300)).outcome()).isEqualTo(BidderOutcome.NO_BID);
    }

    @Test
    void http500YieldsError() {
        wm.stubFor(post("/boom").willReturn(aResponse().withStatus(500)));
        assertThat(client.call(out("/boom", 300)).outcome()).isEqualTo(BidderOutcome.ERROR);
    }

    @Test
    void fanOutIsolatesSlowBidderAsTimeout() {
        wm.stubFor(post("/fast").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"s","bid":[{"id":"b1","impid":"imp-1","price":2.0,"adm":"<div/>"}]}]}
            """)));
        wm.stubFor(post("/slow").willReturn(aResponse().withStatus(204).withFixedDelay(2000)));

        var results = fanOut.fanOut(List.of(out("/fast", 300), out("/slow", 300)));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).outcome()).isEqualTo(BidderOutcome.BID);
        assertThat(results.get(1).outcome()).isEqualTo(BidderOutcome.TIMEOUT);
    }

    @Test
    void mixedHttpAndMockBiddersDispatchCorrectly() {
        wm.stubFor(post("/http-bidder").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"s","bid":[{"id":"b1","impid":"imp-1","price":2.0,"adm":"<div/>"}]}]}
            """)));

        var results = fanOut.fanOut(List.of(
                out("/http-bidder", 300),
                mockOut("m1", MockConfig.builder().price(3.5).build(), 300)));

        assertThat(results).extracting(BidderCallResult::outcome)
                .containsExactly(BidderOutcome.BID, BidderOutcome.BID);
        assertThat(results.get(1).response().getSeatbid().get(0).getBid().get(0).getPrice()).isEqualTo(3.5);
    }

    @Test
    void mockLatencyBeyondDeadlineTimesOut() {
        var results = fanOut.fanOut(List.of(
                mockOut("slow-mock", MockConfig.builder().price(3.0).latencyMs(500).build(), 100)));

        assertThat(results.get(0).outcome()).isEqualTo(BidderOutcome.TIMEOUT);
    }
}
