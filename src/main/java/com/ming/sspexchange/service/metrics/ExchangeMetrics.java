package com.ming.sspexchange.service.metrics;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ming.sspexchange.service.demand.BidderOutcome;
import com.ming.sspexchange.service.supply.RejectReason;

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

    // Requests rejected before an auction runs (invalid ORTB or supply). Answered with 204, kept out of the
    // AUCTION_EVENTS stream (those are real auctions) but counted so rejects stay observable.
    public void reject(RejectReason reason) {
        registry.counter("exchange.reject", "reason", reason.name()).increment();
    }

    // Malformed JSON never reaches the orchestrator — it fails in the message converter. Still a 400.
    public void rejectMalformed() {
        registry.counter("exchange.reject", "reason", "MALFORMED_JSON").increment();
    }
}
