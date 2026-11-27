package com.ming.sspexchange.logging;

import static com.ming.sspexchange.logging.LogField.kv;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

class LogFieldTest {

    @Test
    void kvHoldsReference() {
        var value = Integer.valueOf(1);
        assertThat(kv("a", value).resolvedValue()).isSameAs(value);
    }

    @Test
    void toStringIsKeyEqualsValue() {
        assertThat(kv("a", 1)).hasToString("a=1");
    }

    @Test
    void supplierRunsOnceAcrossReads() {
        var calls = new AtomicInteger();
        var field = kv("a", () -> calls.incrementAndGet());

        field.resolvedValue();
        field.toString();

        assertThat(calls).hasValue(1);
        assertThat(field.resolvedValue()).isEqualTo(1);
    }

    @Test
    void nullSupplierResolvesToNull() {
        assertThat(kv("a", (Supplier<?>) null).resolvedValue()).isNull();
    }

    @Test
    void throwingSupplierResolvesToFailureTextAndRunsOnce() {
        var calls = new AtomicInteger();
        var field = kv("a", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        assertThat(field.resolvedValue()).isEqualTo("supplier failed: java.lang.IllegalStateException: boom");
        field.toString();
        assertThat(calls).hasValue(1);
    }

    @Test
    void toStringSurvivesAValueWhoseToStringThrows() {
        var field = kv("a", new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        });

        assertThat(field.toString()).startsWith("a=").contains("LogFieldTest");
    }

    @Test
    void supplierNeverRunsForDisabledLine() {
        var logger = (Logger) LoggerFactory.getLogger("LogFieldTest.disabled");
        logger.setLevel(Level.OFF);
        var calls = new AtomicInteger();

        logger.info("never", kv("a", () -> calls.incrementAndGet()));

        assertThat(calls).hasValue(0);
    }
}
