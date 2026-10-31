package com.autocare.ai.exception;

import com.autocare.ai.model.dto.ErrorResponse;
import com.azure.core.exception.AzureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps every failure to an {@link ErrorResponse} body (architecture.md §15.4). Dependent-service
 * outages (vector store, LLM, embedding model) become {@code 503} (NFR-005). Messages and logs contain
 * only caller-safe text and request metadata; exception messages, stack traces and PII are not logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String DEPENDENT_SERVICE_MESSAGE =
            "AutoCare AI is temporarily unable to reach a required service. Please retry shortly or "
                    + "contact an authorized service advisor.";
    static final String VALIDATION_MESSAGE = "The request payload is invalid.";
    static final String UNEXPECTED_MESSAGE = "An unexpected error occurred while processing the request.";

    @ExceptionHandler(NoMatchingContextException.class)
    public ResponseEntity<ErrorResponse> handleNoMatchingContext(
            NoMatchingContextException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, NoMatchingContextException.ESCALATION_MESSAGE, request, exception);
    }

    /** Vector store / JDBC outage — the KB cannot be searched, so no grounded answer is possible. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleVectorStoreOutage(
            DataAccessException exception, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, DEPENDENT_SERVICE_MESSAGE, request, exception);
    }

    @ExceptionHandler({
        org.springframework.ai.retry.TransientAiException.class,
        org.springframework.ai.retry.NonTransientAiException.class,
        AzureException.class
    })
    public ResponseEntity<ErrorResponse> handleModelOutage(RuntimeException exception, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, DEPENDENT_SERVICE_MESSAGE, request, exception);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, request, exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return build(
                resolved,
                safeMessage(resolved),
                request,
                exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_MESSAGE, request, exception);
    }

    public static ErrorResponse streamError(Throwable exception, HttpServletRequest request) {
        if (hasNoMatchingContextCause(exception)) {
            return error(HttpStatus.NOT_FOUND, NoMatchingContextException.ESCALATION_MESSAGE, request, exception);
        }
        if (hasCause(exception, DataAccessException.class)
                || hasCause(exception, AzureException.class)
                || hasCause(exception, org.springframework.ai.retry.TransientAiException.class)
                || hasCause(exception, org.springframework.ai.retry.NonTransientAiException.class)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, DEPENDENT_SERVICE_MESSAGE, request, exception);
        }
        if (exception instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
            return error(resolved, safeMessage(resolved), request, exception);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_MESSAGE, request, exception);
    }

    private static boolean hasNoMatchingContextCause(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NoMatchingContextException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable cause = exception;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String safeMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> VALIDATION_MESSAGE;
            case UNAUTHORIZED -> "Authentication is required.";
            case FORBIDDEN -> "Access is forbidden.";
            case NOT_FOUND -> "The requested resource was not found.";
            case TOO_MANY_REQUESTS -> "Rate limit exceeded. Please retry later.";
            case SERVICE_UNAVAILABLE -> DEPENDENT_SERVICE_MESSAGE;
            case CONFLICT -> "The request cannot be completed in its current state.";
            default -> status.is5xxServerError() ? UNEXPECTED_MESSAGE : status.getReasonPhrase();
        };
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request, Exception exception) {
        ErrorResponse body = error(status, message, request, exception);
        return ResponseEntity.status(status).body(body);
    }

    private static ErrorResponse error(
            HttpStatus status, String message, HttpServletRequest request, Throwable exception) {
        ErrorResponse body = ErrorResponseFactory.create(status, message, request);
        log.warn(
                "correlationId={} status={} path={} exceptionType={}",
                body.correlationId(),
                status.value(),
                body.path(),
                exception.getClass().getSimpleName());
        return body;
    }
}
