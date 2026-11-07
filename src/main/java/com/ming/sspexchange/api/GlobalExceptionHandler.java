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
 * requires a throw, and it stays a 400. Every other reject is an empty result from RtbOrchestrator
 * (answered with 204), not an exception.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExchangeMetrics metrics;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> unreadable(HttpMessageNotReadableException exception) {
        log.debug("400 malformed body: {}", exception.getMessage());
        metrics.rejectMalformed();
        return ResponseEntity.badRequest().build();
    }
}
