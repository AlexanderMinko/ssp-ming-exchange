package com.ming.sspexchange.service.demand;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import com.ming.sspexchange.config.BidderClientConfig;
import com.ming.sspexchange.model.entity.Bidder;
import com.ming.sspexchange.model.openrtb.BidRequest;
import com.ming.sspexchange.model.openrtb.BidResponse;
import com.ming.sspexchange.service.mapper.OrtbMapper;

import io.micrometer.observation.ObservationRegistry;
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
    // Highest deadline = 450 - OVERHEAD_MS = 400 ms = the client read timeout (exchange.bidder.read-timeout-ms).
    // A higher tmax would let the read timeout cut a call inside its own window. Raise both together.
    private static final int MAX_TMAX_MS = 450;
    private static final int OVERHEAD_MS = 50;

    private final RestClient bidderRestClient;
    private final OrtbMapper ortbMapper;
    private final ExecutorService bidderExecutor;
    private final ObservationRegistry observationRegistry;

    /**
     * Calls every bidder concurrently. Each call has a verdict future, settled exactly once by whichever comes
     * first: the bidder's reply, or completeOnTimeout's TIMEOUT at the deadline (which starts at submit time).
     * Trade-off: the timeout does not interrupt the call. A timed-out HTTP call keeps its virtual thread (and
     * later its pooled connection) until it ends or the client read timeout ends it — never cancelled, as in
     * ssp-backend.
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
        var verdict = new CompletableFuture<BidderCallResult>()
                .completeOnTimeout(failed(bidder, BidderOutcome.TIMEOUT, timeoutMs), timeoutMs, TimeUnit.MILLISECONDS);
        bidderExecutor.execute(() -> call(bidder, outbound, verdict));
        return verdict;
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

    /**
     * One HTTP call to one bidder; returns what the auction got for it (the verdict). A reply read inside exchange()
     * settles the verdict there; a failure settles it here. Either is a no-op once the timeout has settled it.
     * Package-private so tests can hit it directly against WireMock.
     */
    BidderCallResult call(Bidder bidder, BidRequest outbound, CompletableFuture<BidderCallResult> verdict) {
        verdict.complete(send(bidder, outbound, System.nanoTime(), verdict));
        return verdict.join();
    }

    private BidderCallResult send(Bidder bidder, BidRequest outbound, long startNanos,
            CompletableFuture<BidderCallResult> verdict) {
        try {
            return bidderRestClient.post()
                    .uri(bidder.getEndpoint())
                    .attribute(BidderClientConfig.BIDDER_ATTRIBUTE, bidder.getName())
                    .body(outbound)
                    .exchange((_, response) -> settle(verdict, result(bidder, read(bidder, response), startNanos)));
        } catch (ResourceAccessException exception) {
            // I/O failures and read timeouts are still thrown by the HTTP client — not avoidable here.
            return result(bidder, isTimeout(exception) ? Reply.TIMEOUT : Reply.ERROR, startNanos);
        } catch (RuntimeException exception) {
            // A DSP sent a body dsl-json can't parse. Isolated so one bidder never breaks the auction.
            log.debug("bidder {} error: {}", bidder.getName(), exception.toString());
            return result(bidder, Reply.ERROR, startNanos);
        }
    }

    // exchange() hands us the raw response, so a 204 or a 4xx/5xx is a value, not a thrown
    // RestClientException as with retrieve().
    private Reply read(Bidder bidder, ConvertibleClientHttpResponse response) throws IOException {
        var status = response.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.NO_CONTENT)) {
            return Reply.NO_BID;
        }
        if (!status.is2xxSuccessful()) {
            log.debug("bidder {} returned {}", bidder.getName(), status.value());
            return Reply.ERROR;
        }
        return Reply.of(response.bodyTo(BidResponse.class));
    }

    /**
     * Tries to settle the verdict with the reply, while the client span is still open. complete() is atomic, so the
     * reply and the timeout cannot both win. When the timeout already won, the auction counted TIMEOUT but the span
     * would close as a plain 200/204: recording the marker error on the observation (RestClient keeps it current
     * inside exchange()) makes the span an error, and BidderClientConfig's convention sets outcome=TIMEOUT. The span
     * thus states the auction's verdict, not a clock reading. Nothing is thrown: the marker is preallocated and has
     * no stack trace.
     */
    private BidderCallResult settle(CompletableFuture<BidderCallResult> verdict, BidderCallResult result) {
        if (!verdict.complete(result)) {
            markLate();
        }
        return result;
    }

    private void markLate() {
        var observation = observationRegistry.getCurrentObservation();
        if (Objects.nonNull(observation)) {
            observation.error(BidderClientConfig.LATE_RESPONSE);
        }
    }

    private static BidderCallResult result(Bidder bidder, Reply reply, long startNanos) {
        return new BidderCallResult(bidder, reply.outcome(), reply.body(), elapsedMs(startNanos));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static BidderCallResult failed(Bidder bidder, BidderOutcome outcome, int timeoutMs) {
        return new BidderCallResult(bidder, outcome, null, timeoutMs);
    }

    private static boolean isTimeout(Throwable throwable) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
                .anyMatch(cause -> cause instanceof HttpTimeoutException || cause instanceof TimeoutException);
    }

    /** What the bidder said, before the call's latency is attached. */
    private record Reply(BidderOutcome outcome, BidResponse body) {
        static final Reply NO_BID = new Reply(BidderOutcome.NO_BID, null);
        static final Reply ERROR = new Reply(BidderOutcome.ERROR, null);
        static final Reply TIMEOUT = new Reply(BidderOutcome.TIMEOUT, null);

        static Reply of(BidResponse body) {
            return Objects.nonNull(body) && ObjectUtils.isNotEmpty(body.getSeatbid())
                    ? new Reply(BidderOutcome.BID, body) : NO_BID;
        }
    }
}
