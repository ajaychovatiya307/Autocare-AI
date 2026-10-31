package com.autocare.ai.exception;

import com.autocare.ai.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    public static ErrorResponse create(HttpStatus status, String message, HttpServletRequest request) {
        Object correlation = request == null ? null : request.getAttribute("autocare.correlationId");
        String correlationId = correlation instanceof String value ? value : UUID.randomUUID().toString();
        String path = request == null ? null : request.getRequestURI();
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, correlationId, path);
    }
}
