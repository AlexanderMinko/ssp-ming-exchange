package com.ming.sspexchange.service.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AuctionEventLoggerTest {

    @Test
    void logsOneJsonLinePerAuction() {
        var logger = (Logger) LoggerFactory.getLogger("AUCTION_EVENTS");
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        var eventLogger = new AuctionEventLogger();

        eventLogger.log(new AuctionEvent("req-1", "amz-key-001", "pub-100",
                List.of("BANNER"), List.of("alpha", "beta"),
                List.of(new BidderOutcomeEvent("alpha", "BID", 0, 2.5),
                        new BidderOutcomeEvent("beta", "TIMEOUT", 300, null)),
                "alpha", 2.5, 200, 55));

        assertThat(appender.list).hasSize(1);
        var line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("\"auctionId\":\"req-1\"")
                .contains("\"winner\":\"alpha\"")
                .contains("\"outcome\":\"TIMEOUT\"")
                .contains("\"responseStatus\":200")
                .contains("\"latencyMs\":0");   // zero-latency bidder must still serialize (no skipDefaultValues)
        logger.detachAppender(appender);
    }
}
