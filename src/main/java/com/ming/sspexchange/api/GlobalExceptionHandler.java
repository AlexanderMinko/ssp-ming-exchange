package com.ming.sspexchange.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ming.sspexchange.service.metrics.ExchangeMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Only malformed JSON lands here: dsl-json throws while parsing and Spring's converter contract
 * requires a throw. Every other reject is an {@code RtbOutcome.Rejected} value, not an exception.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExchangeMetrics metrics;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> unreadable(HttpMessageNotReadableException e) {
        log.debug("400 malformed body: {}", e.getMessage());
        metrics.rejectMalformed();
        return ResponseEntity.badRequest().build();
    }
}
