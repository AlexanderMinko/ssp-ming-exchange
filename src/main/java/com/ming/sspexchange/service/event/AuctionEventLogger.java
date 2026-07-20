package com.ming.sspexchange.service.event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;

@Component
public class AuctionEventLogger {

    private static final Logger AUCTION_EVENTS = LoggerFactory.getLogger("AUCTION_EVENTS");
    private static final Logger LOG = LoggerFactory.getLogger(AuctionEventLogger.class);

    // Dedicated dsl-json WITHOUT skipDefaultValues: the auction event is a stable-schema log record,
    // so zero-valued primitives (latencyMs=0, totalLatencyMs=0) must serialize rather than be omitted.
    // The hot-path bean keeps skipDefaultValues on to shrink bid payloads.
    private final DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime().includeServiceLoader());

    public void log(AuctionEvent event) {
        try (var out = new ByteArrayOutputStream()) {
            dslJson.serialize(event, out);
            AUCTION_EVENTS.info(out.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("failed to serialize auction event {}", event.auctionId(), e);
        }
    }
}
