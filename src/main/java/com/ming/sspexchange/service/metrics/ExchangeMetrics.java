package com.ming.sspexchange.service.metrics;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.service.demand.BidderOutcome;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExchangeMetrics {

    private final MeterRegistry registry;

    public void auction(long durationMs) {
        registry.counter("exchange.auctions").increment();
        registry.timer("exchange.auction.duration").record(Duration.ofMillis(durationMs));
    }

    public void noBid(String reason) {
        registry.counter("exchange.nobid", "reason", reason).increment();
    }

    public void bidderResult(String bidder, BidderOutcome outcome, long latencyMs) {
        registry.counter("exchange.bidder.requests", "bidder", bidder, "outcome", outcome.name()).increment();
        registry.timer("exchange.bidder.call.duration", "bidder", bidder).record(Duration.ofMillis(latencyMs));
    }

    public void win(String bidder) {
        registry.counter("exchange.wins", "bidder", bidder).increment();
    }

    // Requests rejected before an auction runs (400 invalid / 403 supply-auth). Kept out of the
    // AUCTION_EVENTS stream (those are real auctions) but counted so rejects stay observable.
    public void reject(int status) {
        registry.counter("exchange.reject", "status", String.valueOf(status)).increment();
    }
}
