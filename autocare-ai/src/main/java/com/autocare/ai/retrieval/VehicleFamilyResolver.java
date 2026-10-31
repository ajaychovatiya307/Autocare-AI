package com.autocare.ai.retrieval;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves/normalizes the {@code vehicle_family} metadata filter value per KB-AUTO-001 §4/§9 (FR-001):
 * known codes pass through unchanged, missing/well-formed-but-unknown codes fall back to
 * {@link #ALL}, and malformed/ambiguous input is flagged for clarification rather than guessed.
 */
@Component
public final class VehicleFamilyResolver {

    /** Metadata value meaning "general guidance, not tied to a specific vehicle family". */
    public static final String ALL = "all";

    /** Canonical shape of a vehicle family code, e.g. {@code AC-SD-P}, {@code AC-EV-1}. */
    private static final Pattern CANONICAL_FORMAT = Pattern.compile("^[A-Z]{2}-[A-Z]{2}-[A-Z0-9]+$");

    /** Families with documented KB coverage (architecture.md §15.1, KB-METADATA.md examples). */
    private static final Set<String> DEFAULT_SUPPORTED_FAMILIES =
            Set.of("AC-HB-P", "AC-SD-P", "AC-SV-P", "AC-SV-D", "AC-EV-1");

    private final Set<String> supportedFamilies;

    public VehicleFamilyResolver() {
        this(DEFAULT_SUPPORTED_FAMILIES);
    }

    public VehicleFamilyResolver(Set<String> supportedFamilies) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String family : supportedFamilies) {
            normalized.add(family.toUpperCase(Locale.ROOT));
        }
        this.supportedFamilies = Collections.unmodifiableSet(normalized);
    }

    /**
     * Resolution outcome: {@code vehicleFamily} is always a safe-to-use metadata filter value
     * ({@code ALL} unless the input was a known, supported code); {@code needsClarification}
     * signals a malformed/ambiguous code that must not be silently guessed.
     */
    public record Resolution(String vehicleFamily, boolean supported, boolean needsClarification) {
    }

    public Resolution resolve(String rawVehicleFamily) {
        if (rawVehicleFamily == null || rawVehicleFamily.isBlank()) {
            return new Resolution(ALL, false, false);
        }

        String trimmed = rawVehicleFamily.trim();
        if (ALL.equalsIgnoreCase(trimmed)) {
            return new Resolution(ALL, true, false);
        }

        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (supportedFamilies.contains(normalized)) {
            return new Resolution(normalized, true, false);
        }

        if (normalized.startsWith("AC-") && !CANONICAL_FORMAT.matcher(normalized).matches()) {
            // Malformed/ambiguous (e.g. missing separators): flag for clarification, never guess.
            return new Resolution(ALL, false, true);
        }

        // Well-formed but not a supported family: fall back to general guidance, no ambiguity.
        return new Resolution(ALL, false, false);
    }
}
