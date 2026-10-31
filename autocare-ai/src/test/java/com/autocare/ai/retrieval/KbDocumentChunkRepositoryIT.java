package com.autocare.ai.retrieval;

import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class KbDocumentChunkRepositoryIT {

    @Autowired
    private KbDocumentChunkRepository repository;

    private static KbDocumentChunkEntity chunk(
            String docCode, String version, DocStatus status, String audience, String vehicleFamily) {
        return KbDocumentChunkEntity.builder()
                .docCode(docCode)
                .version(version)
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(status)
                .audience(audience)
                .vehicleFamily(vehicleFamily)
                .section("2")
                .docType("advisor_operations")
                .content("sample chunk text")
                .embedding(new float[]{0.1f, 0.2f, 0.3f})
                .build();
    }

    @Test
    void findByDocCodeAndVersionReturnsOnlyMatchingChunks() {
        repository.save(chunk("KB-AUTO-007", "v1.0", DocStatus.ACTIVE, "ADVISOR", "all"));
        repository.save(chunk("KB-AUTO-007", "v0.9", DocStatus.RETIRED, "ADVISOR", "all"));

        List<KbDocumentChunkEntity> v1 = repository.findByDocCodeAndVersion("KB-AUTO-007", "v1.0");
        assertThat(v1).hasSize(1);
        assertThat(v1.get(0).getStatus()).isEqualTo(DocStatus.ACTIVE);
    }

    @Test
    void findByDocCodeAndStatusSupportsVersionRetirement() {
        repository.save(chunk("KB-AUTO-002", "v1.0", DocStatus.ACTIVE, "OWNER|ADVISOR", "all"));

        List<KbDocumentChunkEntity> active = repository.findByDocCodeAndStatus("KB-AUTO-002", DocStatus.ACTIVE);
        assertThat(active).hasSize(1);

        KbDocumentChunkEntity toRetire = active.get(0);
        toRetire.retire();
        repository.save(toRetire);

        assertThat(repository.findByDocCodeAndStatus("KB-AUTO-002", DocStatus.ACTIVE)).isEmpty();
        assertThat(repository.findByDocCodeAndStatus("KB-AUTO-002", DocStatus.RETIRED)).hasSize(1);
    }

    @Test
    void findRetrievableChunksEnforcesAudienceAndVehicleFamilyFilters() {
        repository.save(chunk("KB-AUTO-001", "v1.0", DocStatus.ACTIVE, "OWNER|ADVISOR", "all"));
        repository.save(chunk("KB-AUTO-002", "v1.0", DocStatus.ACTIVE, "OWNER|ADVISOR", "AC-EV-1"));
        repository.save(chunk("KB-AUTO-007", "v1.0", DocStatus.ACTIVE, "ADVISOR", "all"));
        repository.save(chunk("KB-AUTO-008", "v1.0", DocStatus.RETIRED, "OWNER|ADVISOR|ADMIN", "all"));

        List<KbDocumentChunkEntity> ownerEvChunks =
                repository.findRetrievableChunks(UserRole.OWNER.name(), "AC-EV-1");
        assertThat(ownerEvChunks)
                .extracting(KbDocumentChunkEntity::getDocCode)
                .containsExactlyInAnyOrder("KB-AUTO-001", "KB-AUTO-002");

        List<KbDocumentChunkEntity> ownerHatchChunks =
                repository.findRetrievableChunks(UserRole.OWNER.name(), "AC-HB-P");
        assertThat(ownerHatchChunks)
                .extracting(KbDocumentChunkEntity::getDocCode)
                .containsExactly("KB-AUTO-001");

        // Advisor-only KB-AUTO-007 must never be returned for an OWNER query.
        assertThat(ownerEvChunks).extracting(KbDocumentChunkEntity::getDocCode).doesNotContain("KB-AUTO-007");

        List<KbDocumentChunkEntity> advisorChunks =
                repository.findRetrievableChunks(UserRole.ADVISOR.name(), "all");
        assertThat(advisorChunks)
                .extracting(KbDocumentChunkEntity::getDocCode)
                .contains("KB-AUTO-001", "KB-AUTO-007");

        // RETIRED chunks are excluded from retrieval regardless of audience/version.
        assertThat(repository.findAll())
                .extracting(KbDocumentChunkEntity::getDocCode)
                .contains("KB-AUTO-008");
        assertThat(advisorChunks).extracting(KbDocumentChunkEntity::getDocCode).doesNotContain("KB-AUTO-008");
    }
}
