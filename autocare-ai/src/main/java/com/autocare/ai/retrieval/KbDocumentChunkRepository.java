package com.autocare.ai.retrieval;

import com.autocare.ai.model.enums.DocStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * KB chunk persistence and metadata-filtered lookups, per architecture.md §5 and KB-METADATA.md §4.
 *
 * <p>Unlike {@code ConversationAuditRepository}, this extends {@link JpaRepository} (full CRUD) because
 * KB version lifecycle management (activate a validated version, retire the superseded one —
 * KB-AUTO-008 §4) requires updating {@code status} on existing rows.
 *
 * <p>These finder methods cover the {@code status}/{@code version}/{@code audience}/{@code
 * vehicle_family} metadata filters. The similarity-threshold filter (≥ 0.50) is applied separately by
 * {@code VectorStoreDocumentRetrieverConfig} (T8.6) via the Spring AI {@code MariaDBVectorStore}, not
 * through this JPA repository.
 */
public interface KbDocumentChunkRepository extends JpaRepository<KbDocumentChunkEntity, Long> {

    List<KbDocumentChunkEntity> findByDocCodeAndVersion(String docCode, String version);

    List<KbDocumentChunkEntity> findByDocCodeOrderByEffectiveDateDescVersionDesc(String docCode);

    List<KbDocumentChunkEntity> findByDocCodeAndStatus(String docCode, DocStatus status);

    List<KbDocumentChunkEntity> findByStatus(DocStatus status);

    /**
     * Chunks retrievable by {@code role} for the given {@code vehicleFamily}: {@code status = ACTIVE},
     * audience contains the role (pipe-delimited match), and vehicle family is either an exact match or
     * the general {@code "all"} guidance (KB-AUTO-001 §4/§9).
     */
    @Query("select c from KbDocumentChunkEntity c "
            + "where c.status = com.autocare.ai.model.enums.DocStatus.ACTIVE "
            + "and c.audience like concat('%', :role, '%') "
            + "and (c.vehicleFamily = :vehicleFamily or c.vehicleFamily = 'all')")
    List<KbDocumentChunkEntity> findRetrievableChunks(
            @Param("role") String role, @Param("vehicleFamily") String vehicleFamily);
}
