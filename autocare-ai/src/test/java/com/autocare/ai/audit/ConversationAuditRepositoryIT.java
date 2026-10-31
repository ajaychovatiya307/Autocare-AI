package com.autocare.ai.audit;

import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ConversationAuditRepositoryIT {

    @Autowired
    private ConversationAuditRepository repository;

    private ConversationAuditRecord newRecord(UUID sessionId, int turnSeq) {
        return ConversationAuditRecord.builder()
                .sessionId(sessionId)
                .turnSeq(turnSeq)
                .actorRole(UserRole.ADVISOR)
                .timestamp(Instant.now())
                .originalQuery("What is the SLA for warranty evidence review?")
                .redactedQuery("What is the SLA for warranty evidence review?")
                .languageDetected("en")
                .translationApplied(false)
                .rewriteApplied(false)
                .expansionApplied(false)
                .transformedQuery("What is the SLA for warranty evidence review?")
                .expandedQueries(List.of())
                .retrievedDocCodes(List.of("KB-AUTO-007"))
                .retrievedChunkRefs(List.of(new SourceCitation("KB-AUTO-007", "v1.0", "4")))
                .queryChatClient(null)
                .answerChatClient("AnswerChatClient")
                .responseText("Within 1 business day, escalated to the warranty coordinator.")
                .citationStatus(CitationStatus.VALID)
                .escalationStatus(false)
                .correlationId(UUID.randomUUID())
                .build();
    }

    @Test
    void savePersistsAppendOnlyRecordAndIsQueryableBySession() {
        UUID sessionId = UUID.randomUUID();
        ConversationAuditRecord saved = repository.save(newRecord(sessionId, 1));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        List<ConversationAuditRecord> found = repository.findBySessionIdOrderByTurnSeqAsc(sessionId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRetrievedDocCodes()).containsExactly("KB-AUTO-007");
        assertThat(found.get(0).getRetrievedChunkRefs())
                .containsExactly(new SourceCitation("KB-AUTO-007", "v1.0", "4"));
        assertThat(repository.countBySessionId(sessionId)).isEqualTo(1);
    }

    @Test
    void multipleTurnsInASessionAreOrderedByTurnSeq() {
        UUID sessionId = UUID.randomUUID();
        repository.save(newRecord(sessionId, 2));
        repository.save(newRecord(sessionId, 1));

        List<ConversationAuditRecord> found = repository.findBySessionIdOrderByTurnSeqAsc(sessionId);
        assertThat(found).extracting(ConversationAuditRecord::getTurnSeq).containsExactly(1, 2);
    }

    @Test
    void repositoryExposesNoUpdateOrDeleteMethods() {
        // Guards the NFR-004 append-only contract: ConversationAuditRepository must extend the bare
        // Repository marker, never CrudRepository/JpaRepository, so no delete*/update* method exists.
        List<String> mutatingMethodNames = Arrays.stream(ConversationAuditRepository.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("delete") || name.startsWith("update"))
                .toList();

        assertThat(mutatingMethodNames).isEmpty();
    }
}
