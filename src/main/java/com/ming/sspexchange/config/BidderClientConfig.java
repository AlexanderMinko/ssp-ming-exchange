package com.ming.sspexchange.config;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.ClientRequestObservationConvention;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestClient;

import com.dslplatform.json.DslJson;

import io.micrometer.common.KeyValues;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.ObservationRegistry;

/** Everything the exchange needs to call bidders: the HTTP client, the RestClient, and the fan-out executor. */
@Configuration
@EnableConfigurationProperties(BidderClientProperties.class)
public class BidderClientConfig {

    /** RestClient request attribute carrying the bidder name; set per call in BidderService, read by the convention. */
    public static final String BIDDER_ATTRIBUTE = "bidder";
    private static final String UNKNOWN_BIDDER = "none";
    private static final String OUTCOME_KEY = "outcome";
    private static final String TIMEOUT_OUTCOME = "TIMEOUT";

    /**
     * Recorded (never thrown) on a bidder call's observation when the response arrives after the auction deadline,
     * see BidderService#settle. One shared instance without a stack trace: it costs nothing per late call.
     */
    public static final RuntimeException LATE_RESPONSE = new LateBidderResponse();

    @Bean
    public HttpClient bidderHttpClient(BidderClientProperties props) {
        // HTTP/1.1: every bidder target in this simulator is cleartext (WireMock in tests, the
        // bidders service in-cluster). An HTTP/2 client attempts h2c and gets RST_STREAM from those
        // HTTP/1.1 servers. Prod would use TLS+ALPN, where HTTP/2 negotiates cleanly.
        return HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .build();
    }

    @Bean
    public RestClient bidderRestClient(DslJson<Object> dslJson, HttpClient bidderHttpClient,
            BidderClientProperties props, ObservationRegistry observationRegistry) {
        // observationRegistry gives every bidder call a client span/timing under the auction's server span.
        var factory = new JdkClientHttpRequestFactory(bidderHttpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        var converter = new DslJsonHttpMessageConverter(dslJson);
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .observationRegistry(observationRegistry)
                .observationConvention(bidderObservationConvention())
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(converter);
                })
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * One virtual thread per bidder call. A new virtual thread inherits no ThreadLocals, so the wrapper captures the
     * caller's context (current observation, MDC) at submit time and restores it in the task. Without it each bidder
     * client span starts its own root trace and the bidder call's log lines carry no traceId.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService bidderExecutor() {
        return ContextExecutorService.wrap(Executors.newVirtualThreadPerTaskExecutor(),
                ContextSnapshotFactory.builder().build());
    }

    /**
     * Names each bidder call after its bidder: span `bid alpha` instead of `http post`, and a `bidder` key value on
     * the span and on http_client_requests_seconds. Low cardinality is safe: names come from our own bidder config.
     */
    static ClientRequestObservationConvention bidderObservationConvention() {   // package-private: unit-tested
        return new DefaultClientRequestObservationConvention() {
            @Override
            public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
                var keyValues = super.getLowCardinalityKeyValues(context).and(BIDDER_ATTRIBUTE, bidderName(context));
                // The response itself was a normal 200/204; the auction counted it as TIMEOUT. Say so.
                if (context.getError() == LATE_RESPONSE) {
                    return keyValues.and(OUTCOME_KEY, TIMEOUT_OUTCOME);
                }
                return keyValues;
            }

            @Override
            public String getContextualName(ClientRequestObservationContext context) {
                return "bid " + bidderName(context);
            }
        };
    }

    private static String bidderName(ClientRequestObservationContext context) {
        if (Objects.isNull(context.getCarrier())) {
            return UNKNOWN_BIDDER;
        }
        return Objects.toString(context.getCarrier().getAttributes().get(BIDDER_ATTRIBUTE), UNKNOWN_BIDDER);
    }

    /** The class name becomes the span's exception type and the `exception` label in SigNoz. */
    static final class LateBidderResponse extends RuntimeException {

        private LateBidderResponse() {
            super("bidder answered after the auction deadline", null, false, false);
        }
    }
}
