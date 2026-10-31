package com.autocare.ai.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A single evidence reference returned alongside a grounded answer, per architecture.md §15.2 (NFR-001).
 */
public record SourceCitation(
        @NotBlank String docCode,
        @NotBlank String version,
        @NotBlank String section,
        Double score) {

    public SourceCitation(String docCode, String version, String section) {
        this(docCode, version, section, null);
    }
}
