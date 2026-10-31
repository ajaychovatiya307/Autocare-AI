package com.autocare.ai.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationAuditRecordTest {

    @Test
    void builderPopulatesAllFieldsIncludingOriginalAndTransformedQuery() {
        UUID sessionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-26T10:15:30Z");
        SourceCitation citation = new SourceCitation("KB-AUTO-007", "v1.0", "4");

        ConversationAuditRecord record = ConversationAuditRecord.builder()
                .sessionId(sessionId)
                .turnSeq(1)
                .actorRole(UserRole.ADVISOR)
                .timestamp(timestamp)
                .originalQuery("What is the SLA for a VIN 1HGCM82633A004352 warranty claim?")
                .redactedQuery("What is the SLA for a [VIN-REDACTED] warranty claim?")
                .languageDetected("en")
                .translationApplied(false)
                .rewriteApplied(true)
                .expansionApplied(false)
                .transformedQuery("What is the SLA for warranty evidence review?")
                .expandedQueries(List.of())
                .retrievedDocCodes(List.of("KB-AUTO-007"))
                .retrievedChunkRefs(List.of(citation))
                .queryChatClient("QueryTransformChatClient")
                .answerChatClient("AnswerChatClient")
                .responseText("The SLA for warranty evidence review is 1 business day.")
                .citationStatus(CitationStatus.VALID)
                .escalationStatus(false)
                .correlationId(correlationId)
                .build();

        assertThat(record.getSessionId()).isEqualTo(sessionId);
        assertThat(record.getTurnSeq()).isEqualTo(1);
        assertThat(record.getActorRole()).isEqualTo(UserRole.ADVISOR);
        assertThat(record.getTimestamp()).isEqualTo(timestamp);
        assertThat(record.getOriginalQuery()).contains("1HGCM82633A004352");
        assertThat(record.getRedactedQuery()).doesNotContain("1HGCM82633A004352").contains("[VIN-REDACTED]");
        assertThat(record.getLanguageDetected()).isEqualTo("en");
        assertThat(record.isTranslationApplied()).isFalse();
        assertThat(record.isRewriteApplied()).isTrue();
        assertThat(record.isExpansionApplied()).isFalse();
        assertThat(record.getTransformedQuery()).isEqualTo("What is the SLA for warranty evidence review?");
        assertThat(record.getExpandedQueries()).isEmpty();
        assertThat(record.getRetrievedDocCodes()).containsExactly("KB-AUTO-007");
        assertThat(record.getRetrievedChunkRefs()).containsExactly(citation);
        assertThat(record.getQueryChatClient()).isEqualTo("QueryTransformChatClient");
        assertThat(record.getAnswerChatClient()).isEqualTo("AnswerChatClient");
        assertThat(record.getResponseText()).isEqualTo("The SLA for warranty evidence review is 1 business day.");
        assertThat(record.getCitationStatus()).isEqualTo(CitationStatus.VALID);
        assertThat(record.isEscalationStatus()).isFalse();
        assertThat(record.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void originalAndTransformedQueryAreIndependentColumns() {
        ConversationAuditRecord record = ConversationAuditRecord.builder()
                .sessionId(UUID.randomUUID())
                .turnSeq(1)
                .actorRole(UserRole.OWNER)
                .timestamp(Instant.now())
                .originalQuery("raw original query")
                .redactedQuery("raw original query")
                .transformedQuery("final standalone query")
                .answerChatClient("AnswerChatClient")
                .responseText("answer")
                .citationStatus(CitationStatus.NOT_APPLICABLE)
                .correlationId(UUID.randomUUID())
                .build();

        assertThat(record.getOriginalQuery()).isNotEqualTo(record.getTransformedQuery());
        assertThat(record.getOriginalQuery()).isEqualTo("raw original query");
        assertThat(record.getTransformedQuery()).isEqualTo("final standalone query");
    }

    @Test
    void exposesNoPublicSettersSoRowsAreImmutableAfterConstruction() {
        boolean hasSetter = java.util.Arrays.stream(ConversationAuditRecord.class.getMethods())
                .anyMatch(method -> isSetterMethod(method));

        assertThat(hasSetter).as("ConversationAuditRecord must not expose setters (append-only, NFR-004)").isFalse();
    }

    private static boolean isSetterMethod(Method method) {
        return method.getName().startsWith("set")
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 1;
    }
}
