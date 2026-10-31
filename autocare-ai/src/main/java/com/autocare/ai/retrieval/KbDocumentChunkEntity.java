package com.autocare.ai.retrieval;

import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One retrievable KB chunk, carrying the metadata that {@code VectorStoreDocumentRetriever} and
 * {@code MetadataFilterBuilder} filter on (architecture.md §5, §13): {@code doc_code}, {@code version},
 * {@code status}, {@code audience}, {@code vehicle_family}, {@code section}, {@code doc_type}.
 *
 * <p>The embedding column must match {@code AutoCareProperties.embedding.dimension} (NFR-008, missing
 * SRS item). This entity does not read {@code AutoCareProperties} itself (JPA entities stay free of
 * Spring context); callers invoke {@link #requireEmbeddingDimension(int)} with the configured
 * dimension (e.g. from {@code AutoCareProperties.embedding().dimension()}) before persisting.
 */
@Entity
@Table(name = "kb_document_chunk")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class KbDocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_code", nullable = false, updatable = false, length = 32)
    private String docCode;

    @Column(name = "version", nullable = false, updatable = false, length = 16)
    private String version;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocStatus status;

    /** Pipe-delimited {@link UserRole} names, e.g. {@code "OWNER|ADVISOR"} or {@code "ADVISOR"} (KB-AUTO-007). */
    @Column(name = "audience", nullable = false, updatable = false, length = 64)
    private String audience;

    /** Supported vehicle family code, or {@code "all"} for general guidance (KB-AUTO-001 §4/§9). */
    @Column(name = "vehicle_family", updatable = false, length = 32)
    private String vehicleFamily;

    @Column(name = "section", updatable = false, length = 32)
    private String section;

    @Column(name = "doc_type", updatable = false, length = 64)
    private String docType;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = JsonFloatArrayConverter.class)
    @Column(name = "embedding", nullable = false, updatable = false, columnDefinition = "TEXT")
    private float[] embedding;

    public int embeddingDimension() {
        return embedding == null ? 0 : embedding.length;
    }

    /**
     * Fails fast when this chunk's embedding does not match the configured
     * {@code autocare.embedding.dimension} (NFR-008), instead of letting a dimension mismatch
     * reach the vector store at query time.
     */
    public void requireEmbeddingDimension(int expectedDimension) {
        int actual = embeddingDimension();
        if (actual != expectedDimension) {
            throw new IllegalStateException(
                    "KB chunk embedding dimension " + actual + " does not match configured "
                            + "autocare.embedding.dimension=" + expectedDimension + " for doc " + docCode);
        }
    }

    public Set<UserRole> audienceRoles() {
        if (audience == null || audience.isBlank()) {
            return EnumSet.noneOf(UserRole.class);
        }
        return Arrays.stream(audience.split("\\|"))
                .map(String::trim)
                .map(UserRole::valueOf)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
    }

    public boolean isRetrievableBy(UserRole role) {
        return audienceRoles().contains(role);
    }

    /** Activates this chunk's version (KB-AUTO-008 §4 ingestion step 6). */
    public void activate() {
        this.status = DocStatus.ACTIVE;
    }

    /** Retires a superseded version so it is excluded from live retrieval but kept for audit (NFR-008). */
    public void retire() {
        this.status = DocStatus.RETIRED;
    }

    public void supersede() {
        this.status = DocStatus.SUPERSEDED;
    }

    public void changeStatus(DocStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }
}
