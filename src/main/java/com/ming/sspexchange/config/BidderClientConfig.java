package com.ming.sspexchange.config;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.dslplatform.json.DslJson;

import io.micrometer.observation.ObservationRegistry;

/** Everything the exchange needs to call bidders: the HTTP client, the RestClient, and the fan-out executor. */
@Configuration
@EnableConfigurationProperties(BidderClientProperties.class)
public class BidderClientConfig {

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
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(converter);
                })
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** One virtual thread per bidder call. */
    @Bean(destroyMethod = "close")
    public ExecutorService bidderExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
