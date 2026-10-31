package com.autocare.ai.exception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import com.azure.core.exception.AzureException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void mockedMariaDbOutageReturnsControlled503ErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataAccessException exception = mock(DataAccessException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/ai/autocare/chat/sync");

        ResponseEntity<com.autocare.ai.model.dto.ErrorResponse> response =
                handler.handleVectorStoreOutage(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.DEPENDENT_SERVICE_MESSAGE);
        assertThat(response.getBody().path()).isEqualTo("/ai/autocare/chat/sync");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void mockedAzureOpenAiOutageReturnsControlled503ErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        AzureException exception = mock(AzureException.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/ai/autocare/chat/sync");

        ResponseEntity<com.autocare.ai.model.dto.ErrorResponse> response =
                handler.handleModelOutage(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().message()).isEqualTo(GlobalExceptionHandler.DEPENDENT_SERVICE_MESSAGE);
        assertThat(response.getBody().path()).isEqualTo("/ai/autocare/chat/sync");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void vectorStoreOutageMapsTo503WithoutLeakingStackTrace() throws Exception {
        mockMvc.perform(get("/test/vector-store-outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.DEPENDENT_SERVICE_MESSAGE))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/test/vector-store-outage"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void transientModelOutageMapsTo503() throws Exception {
        mockMvc.perform(get("/test/model-outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.DEPENDENT_SERVICE_MESSAGE));
    }

    @Test
    void embeddingOutageMapsTo503() throws Exception {
        mockMvc.perform(get("/test/embedding-outage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void noMatchingContextMapsToFixedEscalationMessage() throws Exception {
        mockMvc.perform(get("/test/no-context"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NoMatchingContextException.ESCALATION_MESSAGE));
    }

    @Test
    void invalidPayloadMapsTo400() throws Exception {
        mockMvc.perform(post("/test/validate").contentType("application/json").content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.VALIDATION_MESSAGE));
    }

    @Test
    void responseStatusExceptionDoesNotExposeItsReason() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access is forbidden."));
    }

    @Test
    void responseStatusRateLimitUsesErrorResponseAndSafeMessage() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please retry later."))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/test/rate-limit"));
    }

    @Test
    void wrappedStreamModelOutageMapsTo503WithoutExposingProviderDetails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/ai/autocare/chat/stream");
        AzureException providerFailure = mock(AzureException.class);
        RuntimeException wrappedFailure = new IllegalStateException("provider error", providerFailure);

        com.autocare.ai.model.dto.ErrorResponse response =
                GlobalExceptionHandler.streamError(wrappedFailure, request);

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.message()).isEqualTo(GlobalExceptionHandler.DEPENDENT_SERVICE_MESSAGE);
        assertThat(response.message()).doesNotContain("provider error");
    }

    @Test
    void unexpectedExceptionMapsTo500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNEXPECTED_MESSAGE));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/vector-store-outage")
        String vectorStoreOutage() {
            throw new DataAccessResourceFailureException("mariadb unreachable at 10.0.0.1:3306");
        }

        @GetMapping("/test/model-outage")
        String modelOutage() {
            throw new org.springframework.ai.retry.TransientAiException("azure openai 429");
        }

        @GetMapping("/test/embedding-outage")
        String embeddingOutage() {
            throw new org.springframework.ai.retry.NonTransientAiException("embedding deployment not found");
        }

        @GetMapping("/test/no-context")
        String noContext() {
            throw new NoMatchingContextException();
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "requesterRole must be ADMIN");
        }

        @GetMapping("/test/rate-limit")
        String rateLimit() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "sensitive internal details");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("VIN 1HGCM82633A004352 failed internal lookup");
        }

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody Payload payload) {
            return payload.value();
        }
    }

    record Payload(@NotBlank String value) {
    }
}
