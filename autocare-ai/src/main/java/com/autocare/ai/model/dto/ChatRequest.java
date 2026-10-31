package com.autocare.ai.model.dto;

import com.autocare.ai.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming payload for {@code POST /ai/autocare/chat/sync} and {@code /ai/autocare/chat/stream},
 * per architecture.md §15.1.
 *
 * <p>{@code role} is never trusted on its own: {@code RoleContext} resolves the authenticated
 * principal's role from the security context and rejects the request if it does not match this
 * client-supplied value.
 */
public record ChatRequest(
        @NotBlank String sessionId,
        @NotNull UserRole role,
        String vehicleFamily,
        @NotBlank String query,
        String language) {
}
