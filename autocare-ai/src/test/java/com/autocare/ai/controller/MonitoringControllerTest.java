package com.autocare.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitoringControllerTest {

    private final ConversationAuditRepository repository = mock(ConversationAuditRepository.class);
    private final MonitoringController controller = new MonitoringController(repository);

    @Test
    void noRetrievedDocumentsProducesOrderedFallbackTraceAndEscalation() {
        UUID correlationId = UUID.randomUUID();
        ConversationAuditRecord record = record(correlationId, List.of(), List.of(), true);
        when(repository.findByCorrelationId(correlationId)).thenReturn(List.of(record));

        MonitoringController.MonitoringTurn turn = controller.getByCorrelationId(correlationId);

        assertThat(turn.citationStatus()).isEqualTo("FALLBACK");
        assertThat(turn.escalated()).isTrue();
        assertThat(turn.steps())
                .extracting(MonitoringController.TraceStep::component)
                .containsExactly(
                        "PiiVehicleRedactionAdvisor",
                        "ConversationAuditAdvisor (start)",
                        "SimpleLoggerAdvisor",
                        "TranslationQueryTransformer",
                        "RewriteQueryTransformer",
                        "MultiQueryExpander",
                        "VectorStoreDocumentRetriever",
                        "ContextualQueryAugmenter",
                        "AnswerChatClient",
                        "CitationValidationAdvisor",
                        "ConversationAuditAdvisor (saved, escalated=true)");
        assertThat(turn.steps().get(6).status()).isEqualTo("FALLBACK");
        assertThat(turn.steps()).allSatisfy(step -> assertThat(step.durationMs()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void retrievedDocumentsProducePassTraceAndIncludeRewriteAndVariants() {
        UUID correlationId = UUID.randomUUID();
        ConversationAuditRecord record = record(
                correlationId,
                List.of("WAR-POL-02"),
                List.of(new SourceCitation("WAR-POL-02", "v2", "3.1", 0.82)),
                false);
        when(repository.findByCorrelationId(correlationId)).thenReturn(List.of(record));

        MonitoringController.MonitoringTurn turn = controller.getByCorrelationId(correlationId);

        assertThat(turn.citationStatus()).isEqualTo("PASS");
        assertThat(turn.escalated()).isFalse();
        assertThat(turn.steps().get(4).detail()).isEqualTo("Rewritten query text");
        assertThat(turn.steps().get(6).detail()).contains("WAR-POL-02 §3.1 v2 (score=0.82)");
    }

    @Test
    void returnsAllRolesInMostRecentOrderWhenSessionIsNotSpecified() {
        Instant olderTimestamp = Instant.parse("2026-10-27T10:00:00Z");
        Instant newerTimestamp = Instant.parse("2026-10-27T11:00:00Z");
        ConversationAuditRecord older = record(
                UUID.randomUUID(), List.of(), List.of(), false, UserRole.ADVISOR, olderTimestamp);
        ConversationAuditRecord newer = record(
                UUID.randomUUID(), List.of(), List.of(), false, UserRole.OWNER, newerTimestamp);
        when(repository.findAllByOrderByTimestampDesc()).thenReturn(List.of(newer, older));

        List<MonitoringController.MonitoringTurn> turns = controller.getTurns(null);

        assertThat(turns)
                .extracting(MonitoringController.MonitoringTurn::actor)
                .containsExactly("OWNER", "ADVISOR");
        assertThat(turns)
                .extracting(MonitoringController.MonitoringTurn::timestamp)
                .containsExactly(newerTimestamp.toString(), olderTimestamp.toString());
        verify(repository).findAllByOrderByTimestampDesc();
    }

    private ConversationAuditRecord record(
            UUID correlationId, List<String> documentCodes, List<SourceCitation> citations, boolean escalated) {
        return record(correlationId, documentCodes, citations, escalated,
                UserRole.ADMIN, Instant.parse("2026-10-27T10:00:00Z"));
    }

    private ConversationAuditRecord record(
            UUID correlationId,
            List<String> documentCodes,
            List<SourceCitation> citations,
            boolean escalated,
            UserRole role,
            Instant timestamp) {
        return ConversationAuditRecord.builder()
                .sessionId(UUID.randomUUID())
                .turnSeq(1)
                .actorRole(role)
                .timestamp(timestamp)
                .originalQuery("redacted query")
                .redactedQuery("redacted query")
                .languageDetected("en")
                .translationApplied(false)
                .rewriteApplied(true)
                .expansionApplied(true)
                .transformedQuery("Rewritten query text")
                .expandedQueries(List.of("one", "two"))
                .retrievedDocCodes(documentCodes)
                .retrievedChunkRefs(citations)
                .answerChatClient("AnswerChatClient")
                .responseText("answer")
                .citationStatus(documentCodes.isEmpty() ? CitationStatus.NOT_APPLICABLE : CitationStatus.VALID)
                .escalationStatus(escalated)
                .correlationId(correlationId)
                .createdAt(timestamp.plusMillis(125))
                .build();
    }
}
