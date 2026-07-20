package com.ming.sspexchange;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ming.sspexchange.cache.PartnerConfigCache;
import com.ming.sspexchange.model.entity.AccountEntity;
import com.ming.sspexchange.model.entity.AdFormat;
import com.ming.sspexchange.model.entity.BidderEntity;
import com.ming.sspexchange.model.entity.MockConfig;
import com.ming.sspexchange.model.entity.PartnerStatus;
import com.ming.sspexchange.model.entity.PublisherEntity;
import com.ming.sspexchange.model.entity.Targeting;
import com.ming.sspexchange.repository.AccountRepository;
import com.ming.sspexchange.repository.BidderRepository;
import com.ming.sspexchange.repository.PublisherRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ExchangeIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    static WireMockServer wm = new WireMockServer(0);
    static final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;
    @Autowired AccountRepository accounts;
    @Autowired PublisherRepository publishers;
    @Autowired BidderRepository bidders;
    @Autowired PartnerConfigCache cache;

    @BeforeAll static void startWm() { wm.start(); }
    @AfterAll static void stopWm() { wm.stop(); }

    @BeforeEach
    void seed() {
        wm.resetAll();
        accounts.deleteAll(); publishers.deleteAll(); bidders.deleteAll();

        accounts.save(AccountEntity.builder().id("acc-amz").accountKey("amz-key-001")
                .name("Amazon APS").type("API").status(PartnerStatus.ACTIVE).build());
        publishers.save(PublisherEntity.builder().id("pub-doc-1").accountId("acc-amz")
                .publisherId("pub-100").name("News Pub").status(PartnerStatus.ACTIVE).build());
        bidders.save(BidderEntity.builder().id("bidder-a").name("alpha").seat("seat-alpha")
                .endpoint(wm.baseUrl() + "/bidder-a").status(PartnerStatus.ACTIVE)
                .targeting(Targeting.builder()
                        .formats(Set.of(AdFormat.BANNER, AdFormat.VIDEO)).countries(Set.of()).build())
                .build());
        bidders.save(BidderEntity.builder().id("bidder-b").name("beta").seat("seat-beta")
                .endpoint(wm.baseUrl() + "/bidder-b").status(PartnerStatus.ACTIVE)
                .targeting(Targeting.builder()
                        .formats(Set.of(AdFormat.BANNER)).countries(Set.of()).build())
                .build());

        cache.refresh();
    }

    private HttpResponse<String> sendBid(String accountKey, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d/rtb/bid?account=%s".formatted(port, accountKey)))
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

        var response = sendBid("amz-key-001", fixture());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":\"req-1\"")
                .contains("\"seat\":\"seat-alpha\"")
                .contains("\"price\":2.5")
                .contains("ad for req-1")
                .contains("http://ssp-side/win?price=2.5");
    }

    @Test
    void mockBidderProvidesDemandWithoutAnyDsp() throws Exception {
        bidders.deleteAll();
        bidders.save(BidderEntity.builder().id("bidder-m").name("mock-alpha").seat("seat-mock")
                .endpoint("mock:alpha").status(PartnerStatus.ACTIVE)
                .targeting(Targeting.builder()
                        .formats(Set.of(AdFormat.BANNER, AdFormat.VIDEO)).countries(Set.of()).build())
                .mock(MockConfig.builder().price(3.0).latencyMs(10).bidRate(1.0).build())
                .build());
        cache.refresh();

        var response = sendBid("amz-key-001", fixture());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"seat\":\"seat-mock\"")
                .contains("\"price\":3.0")
                .contains("auction req-1")          // ${AUCTION_ID} substituted in mock adm
                .contains("price=3.0");             // ${AUCTION_PRICE} substituted in mock nurl
        assertThat(wm.getAllServeEvents()).isEmpty();
    }

    @Test
    void allNoBidsReturns204() throws Exception {
        wm.stubFor(post("/bidder-a").willReturn(aResponse().withStatus(204)));
        wm.stubFor(post("/bidder-b").willReturn(aResponse().withStatus(204)));

        assertThat(sendBid("amz-key-001", fixture()).statusCode()).isEqualTo(204);
    }

    @Test
    void unknownAccountReturns403() throws Exception {
        assertThat(sendBid("who-dis", fixture()).statusCode()).isEqualTo(403);
    }

    @Test
    void unknownPublisherReturns403() throws Exception {
        var body = fixture().replace("pub-100", "pub-999");
        assertThat(sendBid("amz-key-001", body).statusCode()).isEqualTo(403);
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        assertThat(sendBid("amz-key-001", "{not json").statusCode()).isEqualTo(400);
    }

    @Test
    void invalidOrtbReturns400() throws Exception {
        var body = fixture().replace("\"imp\"", "\"imp_removed\"");
        assertThat(sendBid("amz-key-001", body).statusCode()).isEqualTo(400);
    }
}
