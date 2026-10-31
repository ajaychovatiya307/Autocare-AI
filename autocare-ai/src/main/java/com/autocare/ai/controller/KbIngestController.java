package com.autocare.ai.controller;

import com.autocare.ai.model.dto.KbIngestRequest;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.service.KbVersionManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /kb/ingest} — Admin-only KB document ingestion (FR-030, architecture.md §9).
 *
 * <p>{@code SecurityConfig} already restricts this path to {@code ROLE_ADMIN}; this controller
 * additionally cross-checks {@link KbIngestRequest#requesterRole()} against the authenticated
 * principal's granted authority, the same defense-in-depth pattern used for {@code ChatRequest.role}
 * (architecture.md §15.1), rather than trusting the client-supplied value alone.
 */
@RestController
@RequestMapping("/kb/ingest")
public class KbIngestController {

    private static final String ADMIN_AUTHORITY = "ROLE_" + UserRole.ADMIN;

    private final KbVersionManagementService versionManagementService;

    public KbIngestController(KbVersionManagementService versionManagementService) {
        this.versionManagementService = versionManagementService;
    }

    @PostMapping
    public ResponseEntity<KbVersionManagementService.IngestionSummary> ingest(
            @Valid @RequestBody KbIngestRequest request, Authentication authentication) {
        if (request.requesterRole() != UserRole.ADMIN || !hasAdminAuthority(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "requesterRole must be ADMIN");
        }
        if (request.status() != DocStatus.RETIRED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "New KB versions must be ingested as RETIRED");
        }

        KbVersionManagementService.IngestionSummary summary = versionManagementService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(ADMIN_AUTHORITY::equals);
    }

}
