package com.ming.sspexchange.service.demand;

import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.ming.sspexchange.model.openrtb.BidResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidderClient {

    private final RestClient bidderRestClient;

    public BidderCallResult call(OutboundRequest out) {
        long start = System.nanoTime();
        try {
            // exchange() hands us the raw response, so a 204 or a 4xx/5xx is a value, not a thrown
            // RestClientException as with retrieve().
            return bidderRestClient.post()
                    .uri(out.bidder().getEndpoint())
                    .body(out.request())
                    .exchange((req, res) -> {
                        var status = res.getStatusCode();
                        if (status.value() == 204) {
                            return result(out, BidderOutcome.NO_BID, null, start);
                        }
                        if (!status.is2xxSuccessful()) {
                            log.debug("bidder {} returned {}", out.bidder().getName(), status.value());
                            return result(out, BidderOutcome.ERROR, null, start);
                        }
                        var body = res.bodyTo(BidResponse.class);
                        var outcome = Objects.isNull(body) || ObjectUtils.isEmpty(body.getSeatbid())
                                ? BidderOutcome.NO_BID : BidderOutcome.BID;
                        return result(out, outcome, body, start);
                    });
        } catch (ResourceAccessException e) {
            // Still thrown by the HTTP client for I/O failures and read timeouts — not avoidable here.
            return result(out, isTimeout(e) ? BidderOutcome.TIMEOUT : BidderOutcome.ERROR, null, start);
        } catch (RuntimeException e) {
            // A DSP sent a body dsl-json can't parse. Isolated so one bidder never breaks the auction.
            log.debug("bidder {} error: {}", out.bidder().getName(), e.toString());
            return result(out, BidderOutcome.ERROR, null, start);
        }
    }

    private static BidderCallResult result(OutboundRequest out, BidderOutcome outcome, BidResponse body, long start) {
        return new BidderCallResult(out.bidder(), outcome, body, Latency.msSince(start));
    }

    private static boolean isTimeout(Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .anyMatch(t -> t instanceof HttpTimeoutException || t instanceof TimeoutException);
    }
}
