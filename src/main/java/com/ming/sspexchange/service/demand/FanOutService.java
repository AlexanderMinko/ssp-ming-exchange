package com.ming.sspexchange.service.demand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FanOutService {

    private final BidderClient bidderClient;
    private final MockBidderClient mockBidderClient;
    private final ExecutorService bidderExecutor;

    public List<BidderCallResult> fanOut(List<OutboundRequest> outs) {
        long startNanos = System.nanoTime();
        // Submit everything first so all bidder calls run concurrently, then collect against each
        // call's own deadline measured from the shared submit time.
        var inFlight = outs.stream()
                .map(out -> new InFlight(out, bidderExecutor.submit(() -> callBidder(out))))
                .toList();

        var results = new ArrayList<BidderCallResult>(inFlight.size());
        for (InFlight f : inFlight) {
            long remainingMs = Math.max(0, f.out().timeoutMs() - Latency.msSince(startNanos));
            try {
                results.add(f.future().get(remainingMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                f.future().cancel(true);   // interrupt the blocking HTTP read / mock sleep
                results.add(failed(f.out(), BidderOutcome.TIMEOUT));
            } catch (ExecutionException e) {
                results.add(failed(f.out(), BidderOutcome.ERROR));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                f.future().cancel(true);
                results.add(failed(f.out(), BidderOutcome.ERROR));
            }
        }
        return results;
    }

    private BidderCallResult callBidder(OutboundRequest out) {
        return out.bidder().isMock() ? mockBidderClient.call(out) : bidderClient.call(out);
    }

    private static BidderCallResult failed(OutboundRequest out, BidderOutcome outcome) {
        return new BidderCallResult(out.bidder(), outcome, null, out.timeoutMs());
    }

    private record InFlight(OutboundRequest out, Future<BidderCallResult> future) { }
}
