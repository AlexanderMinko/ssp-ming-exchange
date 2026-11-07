package com.ming.sspexchange;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ming.sspexchange.cache.BidderCache;
import com.ming.sspexchange.cache.SupplyPartnerCache;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.entity.SupplyPartner;
import com.ming.sspexchange.model.entity.SupplyPartnerType;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.SupplyPartnerRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ExchangeIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    // Started in a static block, not @BeforeAll, so its port exists before the context reads base-url.
    static WireMockServer wm = new WireMockServer(0);
    static { wm.start(); }
    static final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;
    @Autowired SupplyPartnerRepository suppliers;
    @Autowired BidderRepository bidders;
    @Autowired SupplyPartnerCache supplyPartnerCache;
    @Autowired BidderCache bidderCache;

    @DynamicPropertySource
    static void bidderBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("exchange.bidder.base-url", wm::baseUrl);
    }

    @AfterAll static void stopWm() { wm.stop(); }

    @BeforeEach
    void seed() {
        wm.resetAll();
        suppliers.deleteAll(); bidders.deleteAll();

        suppliers.save(SupplyPartner.builder().id("src-doc-1").entityType(SupplyPartnerType.SOURCE)
                .sourceId("amz").name("Amazon source").build());
        suppliers.save(SupplyPartner.builder().id("pub-doc-1").entityType(SupplyPartnerType.PUBLISHER)
                .publisherId("pub-100").name("News Pub").build());
        bidders.save(Bidder.builder().id("bidder-a").name("alpha").seat("seat-alpha")
                .endpoint("/bidder-a").build());
        bidders.save(Bidder.builder().id("bidder-b").name("beta").seat("seat-beta")
                .endpoint("/bidder-b").build());

        supplyPartnerCache.refresh();
        bidderCache.refresh();
    }

    private HttpResponse<String> sendBid(String sourceId, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d/rtb/bid?account=%s".formatted(port, sourceId)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String fixture() throws Exception {
        return new String(getClass().getResourceAsStream("/fixtures/bid-request-full.json").readAllBytes());
    }

    @Test
    void winningAuctionReturns200WithSubstitutedMacros() throws Exception {
        wm.stubFor(post("/bidder-a").willReturn(okJson("""
            {"id":"req-1","seatbid":[{"seat":"dsp-seat","bid":[{"id":"bid-1","impid":"imp-1","price":2.5,
             "adm":"<div>ad for ${AUCTION_ID}</div>",
             "nurl":"http://ssp-side/win?price=${AUCTION_PRICE}",
             "crid":"cr-1","w":320,"h":50,"mtype":1}]}]}
            """)));
        wm.stubFor(post("/bidder-b").willReturn(aResponse().withStatus(204).withFixedDelay(2000)));

        var response = sendBid("amz", fixture());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":\"req-1\"")
                .contains("\"seat\":\"seat-alpha\"")
                .contains("\"price\":2.5")
                .contains("ad for req-1")
                .contains("http://ssp-side/win?price=2.5");
    }

    @Test
    void allNoBidsReturns204() throws Exception {
        wm.stubFor(post("/bidder-a").willReturn(aResponse().withStatus(204)));
        wm.stubFor(post("/bidder-b").willReturn(aResponse().withStatus(204)));

        assertThat(sendBid("amz", fixture()).statusCode()).isEqualTo(204);
    }

    @Test
    void unknownSourceReturns204() throws Exception {
        assertThat(sendBid("zzz", fixture()).statusCode()).isEqualTo(204);
        assertThat(wm.getAllServeEvents()).as("rejected before any bidder call").isEmpty();
    }

    @Test
    void sourceIdOfWrongLengthReturns204() throws Exception {
        assertThat(sendBid("who-dis", fixture()).statusCode()).isEqualTo(204);
        assertThat(wm.getAllServeEvents()).as("rejected before any bidder call").isEmpty();
    }

    @Test
    void unknownPublisherFallsBackToSource() throws Exception {
        wm.stubFor(post("/bidder-a").willReturn(aResponse().withStatus(204)));
        wm.stubFor(post("/bidder-b").willReturn(aResponse().withStatus(204)));
        var body = fixture().replace("pub-100", "pub-999");
        assertThat(sendBid("amz", body).statusCode()).isEqualTo(204);
        assertThat(wm.getAllServeEvents()).as("auction ran under the source's config").hasSize(2);
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        assertThat(sendBid("amz", "{not json").statusCode()).isEqualTo(400);
    }

    @Test
    void invalidOrtbReturns204() throws Exception {
        var body = fixture().replace("\"imp\"", "\"imp_removed\"");
        assertThat(sendBid("amz", body).statusCode()).isEqualTo(204);
        assertThat(wm.getAllServeEvents()).as("rejected before any bidder call").isEmpty();
    }

    @Test
    void duplicatePublisherIdIsRejectedButSourcesAreNot() {
        // Sources have no publisherId; the partial index must not treat them as duplicates.
        suppliers.save(SupplyPartner.builder().id("src-doc-2").entityType(SupplyPartnerType.SOURCE)
                .sourceId("lft").name("Liftoff source").build());

        assertThatThrownBy(() -> suppliers.save(SupplyPartner.builder().id("pub-doc-dup")
                .entityType(SupplyPartnerType.PUBLISHER).publisherId("pub-100")
                .name("Dup Pub").build()))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
