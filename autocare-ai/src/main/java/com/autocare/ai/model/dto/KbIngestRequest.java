package com.autocare.ai.model.dto;

import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payload for {@code POST /kb/ingest} (Admin only), per architecture.md §9 and KB-METADATA.md §1 ingestion tags.
 *
 * <p>{@code requesterRole} carries the authenticated caller's role so {@code KbIngestController} can
 * enforce the Admin-only restriction (FR-030) the same way {@code ChatRequest.role} is cross-checked
 * against the security context.
 */
public record KbIngestRequest(
        @NotBlank String docCode,
        @NotBlank String version,
        @NotNull LocalDate effectiveDate,
        @NotNull DocStatus status,
        @NotBlank String audience,
        String vehicleFamily,
        @NotBlank String section,
        @NotBlank String docType,
        @NotBlank String content,
        @NotNull UserRole requesterRole) {
}
