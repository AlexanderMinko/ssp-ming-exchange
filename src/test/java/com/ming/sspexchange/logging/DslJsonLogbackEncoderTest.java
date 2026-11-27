package com.ming.sspexchange.logging;

import static com.ming.sspexchange.logging.LogField.kv;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

class DslJsonLogbackEncoderTest {

    private static final DslJson<Object> JSON = new DslJson<>(Settings.withRuntime().includeServiceLoader());

    // The application's real Logback context: a bare new LoggerContext() has no MDC adapter, and
    // LoggingEvent.getMDCPropertyMap() would hit null. Production events always come from this context.
    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final DslJsonLogbackEncoder encoder = new DslJsonLogbackEncoder();

    @CompiledJson
    public record Sample(String name, int count) { }

    /**
     * A value whose serialization really fails: dsl-json's generated converter calls the accessor, which throws.
     * (A plain class with only a getter is not enough: dsl-json finds no property and writes {}.)
     */
    @CompiledJson
    public record Exploding(String value) {
        @Override
        public String value() {
            throw new IllegalStateException("boom");
        }

        @Override
        public String toString() {
            return "exploding";
        }
    }

    /** dsl-json finds no property here (a getter without a field or setter) and would write {}. */
    public static class GetterOnly {
        public String getValue() {
            return "hidden";
        }

        @Override
        public String toString() {
            return "getter-only";
        }
    }

    @BeforeEach
    void startEncoder() {
        encoder.setContext(context);
        encoder.setServiceName("exchange");
        encoder.start();
    }

    @Test
    void envelopeFieldsInOrder() {
        var line = encode(event("hello"));

        var keys = List.of("\"@timestamp\"", "\"level\"", "\"logger_name\"", "\"thread_name\"", "\"message\"",
                "\"service_name\"");
        var positions = keys.stream().map(line::indexOf).toList();
        assertThat(positions).doesNotContain(-1).isSorted();
        var fields = parse(line);
        assertThat((String) fields.get("@timestamp")).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z");
        assertThat(fields).containsEntry("level", "INFO").containsEntry("message", "hello")
                .containsEntry("service_name", "exchange");
    }

    @Test
    void mdcIsTopLevel() {
        var event = event("hello");
        var mdc = new HashMap<String, String>();
        mdc.put("traceId", "abc");
        mdc.put("spanId", null);
        event.setMDCPropertyMap(mdc);

        var fields = parse(encode(event));

        assertThat(fields).containsEntry("traceId", "abc").doesNotContainKey("spanId");
    }

    @Test
    void kvGoesUnderPayload() {
        var fields = parse(encode(event("hello", kv("requestId", "int-01"), kv("count", 3))));

        assertThat(payload(fields)).containsEntry("requestId", "int-01").containsEntry("count", 3L);
    }

    @Test
    void noPayloadWhenEmpty() {
        assertThat(parse(encode(event("hello")))).doesNotContainKey("payload");
    }

    @Test
    void keyValuePairsJoinPayloadLastWins() {
        var event = event("hello", kv("a", "x"));
        event.addKeyValuePair(new KeyValuePair("a", "y"));
        event.addKeyValuePair(new KeyValuePair("b", "z"));

        assertThat(payload(parse(encode(event)))).containsEntry("a", "y").containsEntry("b", "z");
    }

    @Test
    void compiledJsonRecordAndCollections() {
        var map = new LinkedHashMap<String, Object>();
        map.put("k", "v");
        var fields = parse(encode(event("hello", kv("record", new Sample("alpha", 2)), kv("map", map),
                kv("list", List.of(1, 2)), kv("nothing", (Object) null))));

        var payload = payload(fields);
        assertThat(asMap(payload.get("record"))).containsEntry("name", "alpha").containsEntry("count", 2L);
        assertThat(asMap(payload.get("map"))).containsEntry("k", "v");
        assertThat(asList(payload.get("list"))).containsExactly(1L, 2L);
        assertThat(payload).containsKey("nothing").containsEntry("nothing", null);
    }

    @Test
    void escapesQuotesNewlinesUnicode() {
        var message = "say \"hi\"\nnow ü";
        var text = "a\\b\"c";

        var line = encode(event(message, kv("text", text)));

        assertThat(line.indexOf('\n')).isEqualTo(line.length() - 1);
        var fields = parse(line);
        assertThat(fields).containsEntry("message", message);
        assertThat(payload(fields)).containsEntry("text", text);
    }

    @Test
    void stackTraceWithCause() {
        var failure = new IllegalStateException("outer", new IllegalArgumentException("inner"));
        var event = new LoggingEvent("fqcn", context.getLogger("test"), Level.ERROR, "failed", failure, null);

        var line = encode(event);

        assertThat(line.indexOf('\n')).isEqualTo(line.length() - 1);
        assertThat((String) parse(line).get("stack_trace"))
                .contains("IllegalStateException").contains("IllegalArgumentException");
    }

    @Test
    void failingValueFallsBackToToString() {
        var fields = parse(encode(event("hello", kv("bad", new Exploding("x")), kv("good", "ok"))));

        assertThat(payload(fields)).containsEntry("bad", "exploding").containsEntry("good", "ok");
        assertThat(fields).containsEntry("message", "hello");
    }

    @Test
    void throwingSupplierKeepsTheLineAndItsSiblings() {
        var fields = parse(encode(event("hello", kv("lazy", () -> {
            throw new IllegalStateException("boom");
        }), kv("good", "ok"))));

        assertThat(payload(fields))
                .containsEntry("lazy", "supplier failed: java.lang.IllegalStateException: boom")
                .containsEntry("good", "ok");
    }

    @Test
    void throwableValueIsWrittenAsItsText() {
        var fields = parse(encode(event("hello", kv("error", new IllegalStateException("boom")))));

        assertThat(payload(fields)).containsEntry("error", "java.lang.IllegalStateException: boom");
    }

    @Test
    void getterOnlyValueFallsBackToToStringInsteadOfEmptyObject() {
        var fields = parse(encode(event("hello", kv("bean", new GetterOnly()))));

        assertThat(payload(fields)).containsEntry("bean", "getter-only");
    }

    @Test
    void supplierRunsOnceAcrossConverterAndEncoder() {
        var calls = new AtomicInteger();
        var event = event("hello", kv("lazy", () -> calls.incrementAndGet()));

        new LogFieldConverter().convert(event);
        encode(event);

        assertThat(calls).hasValue(1);
    }

    private LoggingEvent event(String message, Object... arguments) {
        return new LoggingEvent("fqcn", context.getLogger("test"), Level.INFO, message, null, arguments);
    }

    private String encode(LoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String line) {
        try {
            var bytes = line.getBytes(StandardCharsets.UTF_8);
            return JSON.deserialize(Map.class, bytes, bytes.length);
        } catch (IOException exception) {
            throw new IllegalStateException("not valid JSON: " + line, exception);
        }
    }

    private static Map<String, Object> payload(Map<String, Object> fields) {
        return asMap(fields.get("payload"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
