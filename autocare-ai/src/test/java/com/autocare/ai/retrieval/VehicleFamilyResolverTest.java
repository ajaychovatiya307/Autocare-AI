package com.autocare.ai.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleFamilyResolverTest {

    private final VehicleFamilyResolver resolver = new VehicleFamilyResolver();

    @Test
    void knownCodePassesThroughUnchanged() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("AC-SD-P");

        assertThat(resolution.vehicleFamily()).isEqualTo("AC-SD-P");
        assertThat(resolution.supported()).isTrue();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void knownCodeIsCaseInsensitive() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("ac-sv-d");

        assertThat(resolution.vehicleFamily()).isEqualTo("AC-SV-D");
        assertThat(resolution.supported()).isTrue();
    }

    @Test
    void missingValueFallsBackToAll() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve(null);

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.supported()).isFalse();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void blankValueFallsBackToAll() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("   ");

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void supportedHatchbackCodePassesThroughUnchanged() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("AC-HB-P");

        assertThat(resolution.vehicleFamily()).isEqualTo("AC-HB-P");
        assertThat(resolution.supported()).isTrue();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void supportedPetrolSuvCodePassesThroughUnchanged() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("AC-SV-P");

        assertThat(resolution.vehicleFamily()).isEqualTo("AC-SV-P");
        assertThat(resolution.supported()).isTrue();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void wellFormedButUnsupportedCodeFallsBackToAllWithoutClarification() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("AC-XX-9");

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.supported()).isFalse();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void unsupportedNaturalFamilyNameFallsBackToGeneralGuidance() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("hybrid");

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.supported()).isFalse();
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void malformedAmbiguousCodeIsFlaggedForClarificationRatherThanGuessed() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("ac-ev1");

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.supported()).isFalse();
        assertThat(resolution.needsClarification()).isTrue();
    }

    @Test
    void explicitAllValueIsTreatedAsSupported() {
        VehicleFamilyResolver.Resolution resolution = resolver.resolve("ALL");

        assertThat(resolution.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
        assertThat(resolution.supported()).isTrue();
        assertThat(resolution.needsClarification()).isFalse();
    }
}
