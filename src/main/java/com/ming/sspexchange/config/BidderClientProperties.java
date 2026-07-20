package com.ming.sspexchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("exchange.bidder")
public record BidderClientProperties(
        @DefaultValue("100") int connectTimeoutMs,
        @DefaultValue("1000") int readTimeoutMs) {
}
