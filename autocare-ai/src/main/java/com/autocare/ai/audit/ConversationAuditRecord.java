package com.autocare.ai.audit;

import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Append-only audit row for one conversation turn, per architecture.md §16 (SRS §4.3, §4.4).
 *
 * <p>In addition to the columns listed in architecture.md §16, this entity carries an explicit
 * {@code original_query} column (missing SRS item): the pre-transform query must be retained
 * independently of {@code transformed_query} so both the raw (redacted) ask and the final standalone
 * query sent for retrieval/generation can be audited separately.
 *
 * <p>No column stores raw VIN, registration number, phone, email or address — only post-redaction
 * values (SRS §4.6). The entity exposes no setters: rows are written once via {@link #builder()} and
 * never mutated, consistent with the append-only requirement (NFR-004) enforced at the repository
 * layer by {@code ConversationAuditRepository}.
 */
@Entity
@Table(name = "conversation_audit_record")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConversationAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "turn_seq", nullable = false, updatable = false)
    private Integer turnSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false, length = 16)
    private UserRole actorRole;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "original_query", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String originalQuery;

    @Column(name = "redacted_query", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String redactedQuery;

    @Column(name = "language_detected", updatable = false, length = 32)
    private String languageDetected;

    @Column(name = "translation_applied", nullable = false, updatable = false)
    private boolean translationApplied;

    @Column(name = "rewrite_applied", nullable = false, updatable = false)
    private boolean rewriteApplied;

    @Column(name = "expansion_applied", nullable = false, updatable = false)
    private boolean expansionApplied;

    @Column(name = "transformed_query", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String transformedQuery;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "expanded_queries", updatable = false, columnDefinition = "TEXT")
    private List<String> expandedQueries;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "retrieved_doc_codes", updatable = false, columnDefinition = "TEXT")
    private List<String> retrievedDocCodes;

    @Convert(converter = JsonSourceCitationListConverter.class)
    @Column(name = "retrieved_chunk_refs", updatable = false, columnDefinition = "TEXT")
    private List<SourceCitation> retrievedChunkRefs;

    @Column(name = "query_chat_client", updatable = false, length = 128)
    private String queryChatClient;

    @Column(name = "answer_chat_client", nullable = false, updatable = false, length = 128)
    private String answerChatClient;

    @Column(name = "response_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String responseText;

    @Enumerated(EnumType.STRING)
    @Column(name = "citation_status", nullable = false, updatable = false, length = 16)
    private CitationStatus citationStatus;

    @Column(name = "escalation_status", nullable = false, updatable = false)
    private boolean escalationStatus;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
