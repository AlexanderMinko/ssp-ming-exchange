package com.ming.sspexchange.logging;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One structured field on a log line: {@code log.info("request rejected", kv("requestId", id))}. Passed as an
 * ordinary SLF4J argument; {@link DslJsonLogbackEncoder} writes it under {@code payload}, and
 * {@link LogFieldConverter} prints it on a text console. Same model as Start.io's ssp-backend ({@code LogField} in
 * logging-commons). Kept identical in the exchange and bidders until it moves into a shared library.
 */
public record LogField(String key, Object value) {

    /** A field holding a reference. Nothing is serialized unless the line is written. */
    public static LogField kv(String key, Object value) {
        return new LogField(key, value);
    }

    /**
     * A lazy field: {@code value} runs only when a reader needs it (the encoder, the console converter), at most
     * once. Use it when building the value costs something; a value already in hand needs no lambda.
     */
    public static LogField kv(String key, Supplier<?> value) {
        return new LogField(key, Objects.isNull(value) ? null : new Deferred(value));
    }

    /** The value to write: a lazy field's computed value, otherwise {@link #value()} itself. */
    public Object resolvedValue() {
        return value instanceof Deferred deferred ? deferred.get() : value;
    }

    /** {@code key=value}; a value whose own toString() throws is shown by its class name, never failing the line. */
    @Override
    public String toString() {
        var resolved = resolvedValue();
        try {
            return key + "=" + resolved;
        } catch (RuntimeException toStringFailure) {
            return key + "=" + resolved.getClass().getName();
        }
    }

    /**
     * A value computed on first read and kept. Lock-free, not locked (virtual threads): a racing second reader may
     * compute it too, but no reader sees the supplier gone without the value, because the value is written before
     * the volatile supplier is cleared. A supplier that throws is not retried: its failure becomes the value, as
     * text, so the line and its other fields are still written (Logback would otherwise drop the whole line).
     */
    private static final class Deferred {

        private static final String SUPPLIER_FAILED = "supplier failed: ";

        private volatile Supplier<?> supplier;
        private Object computed;

        private Deferred(Supplier<?> supplier) {
            this.supplier = supplier;
        }

        private Object get() {
            var pending = supplier;
            if (Objects.nonNull(pending)) {
                try {
                    computed = pending.get();
                } catch (RuntimeException supplierFailure) {
                    computed = SUPPLIER_FAILED + supplierFailure;
                }
                supplier = null;
            }
            return computed;
        }

        @Override
        public String toString() {
            return String.valueOf(get());
        }
    }
}
