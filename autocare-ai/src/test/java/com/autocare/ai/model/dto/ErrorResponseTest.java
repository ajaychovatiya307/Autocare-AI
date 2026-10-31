package com.autocare.ai.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void exposesAllAccessors() {
        Instant timestamp = Instant.parse("2026-10-26T10:16:05Z");

        ErrorResponse error = new ErrorResponse(
                timestamp,
                403,
                "Forbidden",
                "Requested role does not match the authenticated principal's role.",
                "7f0a1c3e-2b4d-4a6f-9c3a-1d2e3f4a5b6c",
                "/ai/autocare/chat/sync");

        assertThat(error.timestamp()).isEqualTo(timestamp);
        assertThat(error.status()).isEqualTo(403);
        assertThat(error.error()).isEqualTo("Forbidden");
        assertThat(error.message()).isEqualTo("Requested role does not match the authenticated principal's role.");
        assertThat(error.correlationId()).isEqualTo("7f0a1c3e-2b4d-4a6f-9c3a-1d2e3f4a5b6c");
        assertThat(error.path()).isEqualTo("/ai/autocare/chat/sync");
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        Instant timestamp = Instant.parse("2026-10-26T10:16:05Z");
        ErrorResponse a = new ErrorResponse(timestamp, 503, "Service Unavailable", "msg", "corr-1", "/path");
        ErrorResponse b = new ErrorResponse(timestamp, 503, "Service Unavailable", "msg", "corr-1", "/path");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
