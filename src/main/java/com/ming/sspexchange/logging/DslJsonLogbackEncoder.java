package com.ming.sspexchange.logging;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.runtime.Settings;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

/**
 * One JSON object per line, written with dsl-json. Same model as Start.io's ssp-backend ({@code DslJsonLogbackEncoder}
 * in logging-commons). Kept identical in the exchange and bidders until it moves into a shared library.
 *
 * <p>Field order: {@code @timestamp}, {@code level}, {@code logger_name}, {@code thread_name}, {@code message},
 * {@code service_name}, the MDC entries (top-level: {@code traceId}, {@code spanId}), then {@code payload} holding
 * every {@link LogField} argument and every Logback key-value pair (last one wins on a shared key; left out when
 * empty), then {@code stack_trace}. The SigNoz agents rely on these names (07-signoz.tf).
 *
 * <p>Values: null, strings, numbers, booleans, enums, maps and collections are written here, and a Throwable as its
 * text ({@code class: message}); anything else is serialized by dsl-json ({@code @CompiledJson} records, or runtime
 * reflection). A value dsl-json cannot serialize, or serializes to an empty {@code {}} although it is not a record
 * (a getter-only class: dsl-json found no property), is written as its {@code toString()}, so one bad field never
 * breaks, drops or silently empties the line. No {@code skipDefaultValues}: nulls and zeros are written, so the schema
 * is stable.
 */
public class DslJsonLogbackEncoder extends EncoderBase<ILoggingEvent> {

    private static final String TIMESTAMP_FIELD = "@timestamp";
    private static final String LEVEL_FIELD = "level";
    private static final String LOGGER_NAME_FIELD = "logger_name";
    private static final String THREAD_NAME_FIELD = "thread_name";
    private static final String MESSAGE_FIELD = "message";
    private static final String SERVICE_NAME_FIELD = "service_name";
    private static final String PAYLOAD_FIELD = "payload";
    private static final String STACK_TRACE_FIELD = "stack_trace";

    /** Pinned: the collectors' time_parser reads exactly this layout. */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneOffset.UTC);

    private static final DslJson<Object> JSON = new DslJson<>(Settings.withRuntime().includeServiceLoader());
    private static final int LINE_BUFFER_SIZE = 4096;
    private static final int VALUE_BUFFER_SIZE = 512;
    private static final byte NEW_LINE = '\n';
    private static final byte[] NO_STATIC_FIELDS = new byte[0];

    private final ThrowableProxyConverter throwableConverter = new ThrowableProxyConverter();
    private String serviceName;
    private byte[] staticFields = NO_STATIC_FIELDS;

    /** Set from logback XML ({@code <serviceName>exchange</serviceName>}); must be called before {@link #start()}. */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /** {@code service_name} is constant, so it is serialized once here and appended per line as raw bytes. */
    @Override
    public void start() {
        if (StringUtils.isNotBlank(serviceName)) {
            var writer = JSON.newWriter(VALUE_BUFFER_SIZE);
            writeStringField(writer, SERVICE_NAME_FIELD, serviceName);
            staticFields = writer.toByteArray();
        }
        throwableConverter.setContext(getContext());
        throwableConverter.start();
        super.start();
    }

    @Override
    public void stop() {
        throwableConverter.stop();
        super.stop();
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    // Plain loops below, not streams: this runs once per written line, and a stream per MDC map or argument array
    // is several allocations for two or three entries. A new writer per line: virtual threads are unpooled, so a
    // ThreadLocal buffer would be reused by nobody.
    @Override
    public byte[] encode(ILoggingEvent event) {
        var writer = JSON.newWriter(LINE_BUFFER_SIZE);
        writer.writeByte(JsonWriter.OBJECT_START);
        // @timestamp first and without a leading comma; every later field writes its own leading comma.
        writer.writeString(TIMESTAMP_FIELD);
        writer.writeByte(JsonWriter.SEMI);
        writer.writeString(TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp())));
        writeStringField(writer, LEVEL_FIELD, String.valueOf(event.getLevel()));
        writeStringField(writer, LOGGER_NAME_FIELD, event.getLoggerName());
        writeStringField(writer, THREAD_NAME_FIELD, event.getThreadName());
        writeStringField(writer, MESSAGE_FIELD, event.getFormattedMessage());
        writer.writeRaw(staticFields, 0, staticFields.length);

        var mdc = event.getMDCPropertyMap();
        if (Objects.nonNull(mdc)) {
            for (var entry : mdc.entrySet()) {
                if (Objects.nonNull(entry.getValue())) {
                    writeStringField(writer, entry.getKey(), entry.getValue());
                }
            }
        }

        var payload = payloadFields(event);
        if (!payload.isEmpty()) {
            writer.writeByte(JsonWriter.COMMA);
            writer.writeString(PAYLOAD_FIELD);
            writer.writeByte(JsonWriter.SEMI);
            writeValue(writer, payload);
        }

        if (Objects.nonNull(event.getThrowableProxy())) {
            writeStringField(writer, STACK_TRACE_FIELD, throwableConverter.convert(event));
        }

        writer.writeByte(JsonWriter.OBJECT_END);
        writer.writeByte(NEW_LINE);
        return writer.toByteArray();
    }

    /** LogField arguments first, then Logback key-value pairs, so a pair wins over a kv with the same key. */
    private static Map<String, Object> payloadFields(ILoggingEvent event) {
        var fields = new LinkedHashMap<String, Object>();
        var arguments = event.getArgumentArray();
        if (ObjectUtils.isNotEmpty(arguments)) {
            for (var argument : arguments) {
                if (argument instanceof LogField field) {
                    fields.put(field.key(), field.resolvedValue());
                }
            }
        }
        var pairs = event.getKeyValuePairs();
        if (Objects.nonNull(pairs)) {
            for (var pair : pairs) {
                fields.put(pair.key, pair.value);
            }
        }
        return fields;
    }

    private static void writeStringField(JsonWriter writer, String key, String value) {
        writer.writeByte(JsonWriter.COMMA);
        writer.writeString(key);
        writer.writeByte(JsonWriter.SEMI);
        if (Objects.isNull(value)) {
            writer.writeNull();
        } else {
            writer.writeString(value);
        }
    }

    private static void writeValue(JsonWriter writer, Object value) {
        switch (value) {
            case null -> writer.writeNull();
            case String text -> writer.writeString(text);
            case Boolean flag -> writer.writeAscii(flag.toString());
            case Double number when !Double.isFinite(number) -> writer.writeString(number.toString());
            case Float number when !Float.isFinite(number) -> writer.writeString(number.toString());
            case Number number -> writer.writeAscii(number.toString());
            case Enum<?> constant -> writer.writeString(constant.name());
            case Map<?, ?> map -> writeMap(writer, map);
            case Collection<?> collection -> writeCollection(writer, collection);
            case Throwable failure -> writer.writeString(safeToString(failure));
            default -> writeSerialized(writer, value);
        }
    }

    private static void writeMap(JsonWriter writer, Map<?, ?> map) {
        writer.writeByte(JsonWriter.OBJECT_START);
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                writer.writeByte(JsonWriter.COMMA);
            }
            first = false;
            writer.writeString(String.valueOf(entry.getKey()));
            writer.writeByte(JsonWriter.SEMI);
            writeValue(writer, entry.getValue());
        }
        writer.writeByte(JsonWriter.OBJECT_END);
    }

    private static void writeCollection(JsonWriter writer, Collection<?> collection) {
        writer.writeByte(JsonWriter.ARRAY_START);
        var first = true;
        for (var element : collection) {
            if (!first) {
                writer.writeByte(JsonWriter.COMMA);
            }
            first = false;
            writeValue(writer, element);
        }
        writer.writeByte(JsonWriter.ARRAY_END);
    }

    /**
     * dsl-json writes into a scratch writer first, so a value that fails half-way leaves nothing behind in the line;
     * it is then written as its toString() (or its class name, if even that throws). An empty {} from a non-record
     * means dsl-json saw no property, so the toString() says more than the {} would.
     */
    private static void writeSerialized(JsonWriter writer, Object value) {
        var scratch = JSON.newWriter(VALUE_BUFFER_SIZE);
        try {
            JSON.serialize(scratch, value);
            var bytes = scratch.toByteArray();
            if (isEmptyObject(bytes) && !value.getClass().isRecord()) {
                writer.writeString(safeToString(value));
            } else {
                writer.writeRaw(bytes, 0, bytes.length);
            }
        } catch (IOException | RuntimeException serializationFailure) {
            writer.writeString(safeToString(value));
        }
    }

    private static boolean isEmptyObject(byte[] bytes) {
        return bytes.length == 2 && bytes[0] == JsonWriter.OBJECT_START && bytes[1] == JsonWriter.OBJECT_END;
    }

    private static String safeToString(Object value) {
        try {
            return String.valueOf(value);
        } catch (RuntimeException toStringFailure) {
            return value.getClass().getName();
        }
    }
}
