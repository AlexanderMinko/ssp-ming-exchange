package com.ming.sspexchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code baseUrl} is the bidders service (BIDDERS_BASE_URL). Each bidder's {@code endpoint} is a path
 * resolved against it, so Mongo never holds cluster DNS names. The default matches the local compose.
 */
@ConfigurationProperties("exchange.bidder")
public record BidderClientProperties(
        @DefaultValue("http://localhost:8081") String baseUrl,
        @DefaultValue("100") int connectTimeoutMs,
        @DefaultValue("1000") int readTimeoutMs) {
}
