package com.ming.sspexchange.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ming.sspexchange.service.metrics.ExchangeMetrics;
import com.ming.sspexchange.service.supply.InvalidBidRequestException;
import com.ming.sspexchange.service.supply.SupplyAuthException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExchangeMetrics metrics;

    @ExceptionHandler(SupplyAuthException.class)
    public ResponseEntity<Void> supplyAuth(SupplyAuthException e) {
        log.debug("403: {}", e.getMessage());
        metrics.reject(HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(InvalidBidRequestException.class)
    public ResponseEntity<Void> invalidRequest(InvalidBidRequestException e) {
        log.debug("400: {}", e.getMessage());
        metrics.reject(HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> unreadable(HttpMessageNotReadableException e) {
        log.debug("400 malformed body: {}", e.getMessage());
        metrics.reject(HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().build();
    }
}
