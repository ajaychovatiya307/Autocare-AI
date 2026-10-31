package com.autocare.ai.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Append-only access to {@link ConversationAuditRecord} (NFR-004, SRS §4.3). Deliberately extends the
 * bare Spring Data {@link Repository} marker interface instead of {@code CrudRepository}/{@code
 * JpaRepository}: no {@code delete*}/{@code update} method is exposed, only {@link #save} (insert,
 * since every column besides {@code id} is {@code updatable = false}) and read queries.
 */
public interface ConversationAuditRepository extends Repository<ConversationAuditRecord, Long> {

    ConversationAuditRecord save(ConversationAuditRecord record);

    List<ConversationAuditRecord> findBySessionIdOrderByTurnSeqAsc(UUID sessionId);

    List<ConversationAuditRecord> findAllByOrderByTimestampDesc();

    List<ConversationAuditRecord> findByCorrelationId(UUID correlationId);

    List<ConversationAuditRecord> findByOriginalQueryStartingWithOrderByTimestampDesc(String prefix);

    long countBySessionId(UUID sessionId);
}
