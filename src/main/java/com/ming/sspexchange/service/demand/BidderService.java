package com.ming.sspexchange.service.demand;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.mapper.OrtbMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The demand side: the tmax policy, the concurrent fan-out and the HTTP call to one bidder.
 * Every outcome (bid, no-bid, timeout, error) is a {@link BidderCallResult} value — nothing is thrown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidderService {

    private static final int DEFAULT_TMAX_MS = 300;
    private static final int MIN_TMAX_MS = 100;
    private static final int MAX_TMAX_MS = 1000;
    private static final int OVERHEAD_MS = 50;

    private final RestClient bidderRestClient;
    private final OrtbMapper ortbMapper;
    private final ExecutorService bidderExecutor;

    /**
     * Calls every bidder concurrently. Each call's deadline starts at submit time. A late bidder
     * becomes a TIMEOUT value via completeOnTimeout.
     * Trade-off: completeOnTimeout does not interrupt the task. A timed-out HTTP call keeps its
     * virtual thread until the client read timeout ends it.
     */
    public List<BidderCallResult> fanOut(BidRequest request, List<Bidder> bidders) {
        int effectiveTmax = effectiveTmax(request.getTmax());
        var calls = bidders.stream()
                .map(bidder -> submit(bidder, request, effectiveTmax))
                .toList();
        return calls.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private CompletableFuture<BidderCallResult> submit(Bidder bidder, BidRequest request, int effectiveTmax) {
        int timeoutMs = outboundTimeout(effectiveTmax, bidder.getTmaxMs());
        // Each bidder gets its own copy because tmax differs per bidder and the calls serialize concurrently.
        var outbound = ortbMapper.copyWithTmax(request, timeoutMs);
        return CompletableFuture.supplyAsync(() -> call(bidder, outbound), bidderExecutor)
                .exceptionally(_ -> failed(bidder, BidderOutcome.ERROR, timeoutMs))   // safety net; call() never throws
                .completeOnTimeout(failed(bidder, BidderOutcome.TIMEOUT, timeoutMs), timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** The request's tmax, or the default when absent, clamped to [MIN_TMAX_MS, MAX_TMAX_MS]. */
    public static int effectiveTmax(Integer requested) {
        return Math.clamp(Objects.nonNull(requested) ? requested : DEFAULT_TMAX_MS, MIN_TMAX_MS, MAX_TMAX_MS);
    }

    // Exchange overhead comes off the top; a bidder's own tmaxMs can only shorten its deadline, never extend it.
    static int outboundTimeout(int effectiveTmax, Integer bidderOverrideMs) {
        int budget = effectiveTmax - OVERHEAD_MS;
        return Objects.nonNull(bidderOverrideMs) ? Math.min(budget, bidderOverrideMs) : budget;
    }

    /** One HTTP call to one bidder. Package-private so tests can hit it directly against WireMock. */
    BidderCallResult call(Bidder bidder, BidRequest outbound) {
        long start = System.nanoTime();
        try {
            // exchange() hands us the raw response, so a 204 or a 4xx/5xx is a value, not a thrown
            // RestClientException as with retrieve().
            return bidderRestClient.post()
                    .uri(bidder.getEndpoint())
                    .body(outbound)
                    .exchange((clientRequest, clientResponse) -> {
                        var status = clientResponse.getStatusCode();
                        if (status.value() == 204) {
                            return result(bidder, BidderOutcome.NO_BID, null, start);
                        }
                        if (!status.is2xxSuccessful()) {
                            log.debug("bidder {} returned {}", bidder.getName(), status.value());
                            return result(bidder, BidderOutcome.ERROR, null, start);
                        }
                        var body = clientResponse.bodyTo(BidResponse.class);
                        var outcome = Objects.isNull(body) || ObjectUtils.isEmpty(body.getSeatbid())
                                ? BidderOutcome.NO_BID : BidderOutcome.BID;
                        return result(bidder, outcome, body, start);
                    });
        } catch (ResourceAccessException exception) {
            // Still thrown by the HTTP client for I/O failures and read timeouts — not avoidable here.
            return result(bidder, isTimeout(exception) ? BidderOutcome.TIMEOUT : BidderOutcome.ERROR, null, start);
        } catch (RuntimeException exception) {
            // A DSP sent a body dsl-json can't parse. Isolated so one bidder never breaks the auction.
            log.debug("bidder {} error: {}", bidder.getName(), exception.toString());
            return result(bidder, BidderOutcome.ERROR, null, start);
        }
    }

    private static BidderCallResult result(Bidder bidder, BidderOutcome outcome, BidResponse body, long startNanos) {
        return new BidderCallResult(bidder, outcome, body, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static BidderCallResult failed(Bidder bidder, BidderOutcome outcome, int timeoutMs) {
        return new BidderCallResult(bidder, outcome, null, timeoutMs);
    }

    private static boolean isTimeout(Throwable throwable) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
                .anyMatch(cause -> cause instanceof HttpTimeoutException || cause instanceof TimeoutException);
    }
}
