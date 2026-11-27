package com.ming.sspexchange.logging;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code %kvargs} in a text pattern: appends each {@link LogField} argument as {@code key=value}. SLF4J renders an
 * argument only through a {@code {}} placeholder, and {@code log.info("message", kv(...))} has none, so without this
 * the laptop console would show bare messages.
 */
public class LogFieldConverter extends ClassicConverter {

    private static final String EMPTY = "";
    private static final String SEPARATOR = " ";

    @Override
    public String convert(ILoggingEvent event) {
        var arguments = event.getArgumentArray();
        if (ObjectUtils.isEmpty(arguments)) {
            return EMPTY;
        }
        var rendered = Arrays.stream(arguments)
                .filter(LogField.class::isInstance)
                .map(String::valueOf)
                .collect(Collectors.joining(SEPARATOR));
        return rendered.isEmpty() ? EMPTY : SEPARATOR + rendered;
    }
}
