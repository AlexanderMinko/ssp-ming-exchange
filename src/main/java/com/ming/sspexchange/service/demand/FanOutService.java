package com.ming.sspexchange.service.demand;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FanOutService {

    private final BidderClient bidderClient;
    private final MockBidderClient mockBidderClient;
    private final ExecutorService bidderExecutor;

    /**
     * Calls every bidder concurrently. Each call's deadline starts at submit time. A late bidder
     * becomes a TIMEOUT value via completeOnTimeout — nothing is thrown on the hot path.
     * Trade-off: completeOnTimeout does not interrupt the task. A timed-out HTTP call keeps its
     * virtual thread until the client read timeout ends it; a mock keeps sleeping (cheap).
     */
    public List<BidderCallResult> fanOut(List<OutboundRequest> outs) {
        var calls = outs.stream()
                .map(out -> CompletableFuture.supplyAsync(() -> callBidder(out), bidderExecutor)
                        .exceptionally(e -> failed(out, BidderOutcome.ERROR))   // safety net; clients don't throw
                        .completeOnTimeout(failed(out, BidderOutcome.TIMEOUT), out.timeoutMs(), TimeUnit.MILLISECONDS))
                .toList();
        return calls.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private BidderCallResult callBidder(OutboundRequest out) {
        return out.bidder().isMock() ? mockBidderClient.call(out) : bidderClient.call(out);
    }

    private static BidderCallResult failed(OutboundRequest out, BidderOutcome outcome) {
        return new BidderCallResult(out.bidder(), outcome, null, out.timeoutMs());
    }
}
