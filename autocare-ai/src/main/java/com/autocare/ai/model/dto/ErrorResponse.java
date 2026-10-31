package com.autocare.ai.model.dto;

import java.time.Instant;

/**
 * Error body returned by {@code GlobalExceptionHandler} for every failed request, per architecture.md §15.4.
 *
 * <p>{@code message} must never leak raw PII, VIN, registration numbers, stack traces or internal
 * query text (NFR-003).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String correlationId,
        String path) {
}
