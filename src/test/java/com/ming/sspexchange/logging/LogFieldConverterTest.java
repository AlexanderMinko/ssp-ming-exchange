package com.ming.sspexchange.logging;

import static com.ming.sspexchange.logging.LogField.kv;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

class LogFieldConverterTest {

    private final LoggerContext context = new LoggerContext();
    private final LogFieldConverter converter = new LogFieldConverter();

    @Test
    void rendersOnlyLogFields() {
        assertThat(converter.convert(event(kv("a", 1), "plain"))).isEqualTo(" a=1");
    }

    @Test
    void emptyWhenNoArgs() {
        assertThat(converter.convert(event())).isEmpty();
    }

    private LoggingEvent event(Object... arguments) {
        return new LoggingEvent("fqcn", context.getLogger("test"), Level.INFO, "message", null, arguments);
    }
}
