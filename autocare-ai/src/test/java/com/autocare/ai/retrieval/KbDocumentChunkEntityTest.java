package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbDocumentChunkEntityTest {

    private static final int CONFIGURED_DIMENSION =
            new AutoCareProperties(
                            new AutoCareProperties.Rag(0.50, false),
                            new AutoCareProperties.Embedding(1536),
                            new AutoCareProperties.Chat("gpt-5-1", "gpt-5", 20),
                            new AutoCareProperties.Kb("v1.0"),
                            new AutoCareProperties.RateLimit(10, 10, 60))
                    .embedding()
                    .dimension();

    private KbDocumentChunkEntity chunk(float[] embedding, String audience) {
        return KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-007")
                .version("v1.0")
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(DocStatus.ACTIVE)
                .audience(audience)
                .vehicleFamily("all")
                .section("2")
                .docType("advisor_operations")
                .content("Mandatory Intake Checklist ...")
                .embedding(embedding)
                .build();
    }

    @Test
    void storesMetadataFieldsUsedByRetrievalFilters() {
        KbDocumentChunkEntity entity = chunk(new float[CONFIGURED_DIMENSION], "ADVISOR");

        assertThat(entity.getDocCode()).isEqualTo("KB-AUTO-007");
        assertThat(entity.getVersion()).isEqualTo("v1.0");
        assertThat(entity.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(entity.getStatus()).isEqualTo(DocStatus.ACTIVE);
        assertThat(entity.getVehicleFamily()).isEqualTo("all");
        assertThat(entity.getSection()).isEqualTo("2");
        assertThat(entity.getDocType()).isEqualTo("advisor_operations");
    }

    @Test
    void embeddingDimensionMatchesConfiguredAutoCareProperties() {
        KbDocumentChunkEntity entity = chunk(new float[CONFIGURED_DIMENSION], "OWNER|ADVISOR");

        assertThat(entity.embeddingDimension()).isEqualTo(CONFIGURED_DIMENSION);
        assertThat(entity.embeddingDimension()).isEqualTo(1536);
        entity.requireEmbeddingDimension(CONFIGURED_DIMENSION);
    }

    @Test
    void requireEmbeddingDimensionFailsFastOnMismatch() {
        KbDocumentChunkEntity entity = chunk(new float[CONFIGURED_DIMENSION - 1], "OWNER|ADVISOR");

        assertThatThrownBy(() -> entity.requireEmbeddingDimension(CONFIGURED_DIMENSION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match")
                .hasMessageContaining("KB-AUTO-007");
    }

    @Test
    void audienceParsingDrivesRoleBasedRetrieval() {
        KbDocumentChunkEntity advisorOnly = chunk(new float[CONFIGURED_DIMENSION], "ADVISOR");
        assertThat(advisorOnly.audienceRoles()).containsExactly(UserRole.ADVISOR);
        assertThat(advisorOnly.isRetrievableBy(UserRole.ADVISOR)).isTrue();
        assertThat(advisorOnly.isRetrievableBy(UserRole.OWNER)).isFalse();

        KbDocumentChunkEntity ownerAndAdvisor = chunk(new float[CONFIGURED_DIMENSION], "OWNER|ADVISOR");
        assertThat(ownerAndAdvisor.audienceRoles()).containsExactlyInAnyOrder(UserRole.OWNER, UserRole.ADVISOR);
        assertThat(ownerAndAdvisor.isRetrievableBy(UserRole.OWNER)).isTrue();
        assertThat(ownerAndAdvisor.isRetrievableBy(UserRole.ADMIN)).isFalse();
    }
}
