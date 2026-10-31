package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataFilterBuilderTest {

    private final AutoCareProperties properties = new AutoCareProperties(
            new AutoCareProperties.Rag(0.50, false),
            new AutoCareProperties.Embedding(1536),
            new AutoCareProperties.Chat("gpt-4o-mini", "gpt-4o", 20),
            new AutoCareProperties.Kb("v1.0"),
            new AutoCareProperties.RateLimit(10, 10, 60));

    private final MetadataFilterBuilder builder = new MetadataFilterBuilder(new VehicleFamilyResolver(), properties);

    @Test
    void reflectsAuthenticatedRoleAndResolvedVehicleFamilyAndActiveStatusAcrossEffectiveVersions() {
        MetadataFilterBuilder.Filter filter = builder.build(UserRole.ADVISOR, "AC-SD-P");

        assertThat(filter.status()).isEqualTo(DocStatus.ACTIVE);
        assertThat(filter.version()).isNull();
        assertThat(filter.role()).isEqualTo(UserRole.ADVISOR);
        assertThat(filter.vehicleFamily()).isEqualTo("AC-SD-P");
    }

    @Test
    void fallsBackToAllForMissingVehicleFamily() {
        MetadataFilterBuilder.Filter filter = builder.build(UserRole.OWNER, null);

        assertThat(filter.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
    }

    @Test
    void fallsBackToAllForUnsupportedVehicleFamily() {
        MetadataFilterBuilder.Filter filter = builder.build(UserRole.OWNER, "AC-XX-9");

        assertThat(filter.vehicleFamily()).isEqualTo(VehicleFamilyResolver.ALL);
    }

    @Test
    void preservesSupportedHatchbackFamily() {
        MetadataFilterBuilder.Filter filter = builder.build(UserRole.OWNER, "AC-HB-P");

        assertThat(filter.vehicleFamily()).isEqualTo("AC-HB-P");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(() -> builder.build(null, "AC-SD-P")).isInstanceOf(IllegalArgumentException.class);
    }
}
