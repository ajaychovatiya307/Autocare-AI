package com.autocare.ai.advisor;

import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.security.RedactionPatterns;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

/**
 * Advisor chain stage 2 (architecture.md §17.2/§17.13): opens an in-flight audit record from the
 * already-redacted query in {@link #before}, then finalizes and persists one append-only
 * {@link ConversationAuditRecord} row per turn in {@link #after}, once every downstream advisor/stage
 * has contributed its context entries (SRS §4.3, NFR-004).
 *
 * <p>Downstream stages communicate with this advisor exclusively through the {@code ChatClientRequest}
 * / {@code ChatClientResponse} context map, using the {@code CTX_*} keys declared below. {@link #before}
 * requires {@link #CTX_SESSION_ID} and {@link #CTX_ACTOR_ROLE} to already be present (set by the caller,
 * e.g. {@code ChatService}, via {@code ChatClient.advisors(a -> a.param(...))}); {@link #after} requires
 * {@link #CTX_ANSWER_CHAT_CLIENT} to be present, since every finalized record must name the ChatClient
 * that produced (or would have produced) the answer, per SRS §4.7.
 */
@Component
public class ConversationAuditAdvisor implements BaseAdvisor {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;
    private static final Logger logger = LoggerFactory.getLogger(ConversationAuditAdvisor.class);

    public static final String CTX_SESSION_ID = "autocare.audit.sessionId";
    public static final String CTX_ACTOR_ROLE = "autocare.audit.actorRole";
    public static final String CTX_CORRELATION_ID = "autocare.audit.correlationId";
    public static final String CTX_LANGUAGE_DETECTED = "autocare.audit.languageDetected";
    public static final String CTX_TRANSLATION_APPLIED = "autocare.audit.translationApplied";
    public static final String CTX_REWRITE_APPLIED = "autocare.audit.rewriteApplied";
    public static final String CTX_EXPANSION_APPLIED = "autocare.audit.expansionApplied";
    public static final String CTX_TRANSFORMED_QUERY = "autocare.audit.transformedQuery";
    public static final String CTX_EXPANDED_QUERIES = "autocare.audit.expandedQueries";
    public static final String CTX_RETRIEVED_DOC_CODES = "autocare.audit.retrievedDocCodes";
    public static final String CTX_RETRIEVED_CHUNK_REFS = "autocare.audit.retrievedChunkRefs";
    public static final String CTX_QUERY_CHAT_CLIENT = "autocare.audit.queryChatClient";
    public static final String CTX_ANSWER_CHAT_CLIENT = "autocare.audit.answerChatClient";
    public static final String CTX_RESPONSE_TEXT_OVERRIDE = "autocare.audit.responseTextOverride";
    public static final String CTX_CITATION_STATUS = "autocare.audit.citationStatus";
    public static final String CTX_ESCALATED = "autocare.audit.escalated";

    private static final String CTX_PENDING = "autocare.audit.pending";

    private final ConversationAuditRepository auditRepository;

    public ConversationAuditAdvisor(ConversationAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        Map<String, Object> context = request.context();

        Object sessionIdValue = context.get(CTX_SESSION_ID);
        Assert.notNull(sessionIdValue, "Missing required advisor context entry: " + CTX_SESSION_ID);
        UUID sessionId = toSessionId(sessionIdValue);

        Object actorRoleValue = context.get(CTX_ACTOR_ROLE);
        Assert.notNull(actorRoleValue, "Missing required advisor context entry: " + CTX_ACTOR_ROLE);
        UserRole actorRole = (UserRole) actorRoleValue;

        String redactedQuery = request.prompt().getUserMessages().isEmpty()
                ? ""
                : request.prompt().getUserMessage().getText();

        UUID correlationId = context.containsKey(CTX_CORRELATION_ID)
                ? toSessionId(context.get(CTX_CORRELATION_ID))
                : UUID.randomUUID();

        int turnSeq = (int) auditRepository.countBySessionId(sessionId) + 1;

        PendingAudit pending = new PendingAudit(
                sessionId, turnSeq, actorRole, Instant.now(), redactedQuery, correlationId);
        logger.info(
                "advisor={} action=turn_started sessionId={} turnSeq={} correlationId={}",
                getName(),
                sessionId,
                turnSeq,
                correlationId);

        return request.mutate()
                .context(CTX_PENDING, pending)
                .context(CTX_CORRELATION_ID, correlationId)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        Map<String, Object> context = response.context();
        Object pendingValue = context.get(CTX_PENDING);
        if (!(pendingValue instanceof PendingAudit pending)) {
            // before() never ran for this exchange (e.g. advisor misconfigured out of order); nothing to finalize.
            return response;
        }

        Object answerChatClient = context.get(CTX_ANSWER_CHAT_CLIENT);
        Assert.hasText((String) answerChatClient,
                "Missing required advisor context entry: " + CTX_ANSWER_CHAT_CLIENT);

        ConversationAuditRecord record = ConversationAuditRecord.builder()
                .sessionId(pending.sessionId())
                .turnSeq(pending.turnSeq())
                .actorRole(pending.actorRole())
                .timestamp(pending.timestamp())
                .originalQuery(pending.redactedQuery())
                .redactedQuery(pending.redactedQuery())
                .languageDetected((String) context.get(CTX_LANGUAGE_DETECTED))
                .translationApplied(booleanOf(context, CTX_TRANSLATION_APPLIED))
                .rewriteApplied(booleanOf(context, CTX_REWRITE_APPLIED))
                .expansionApplied(booleanOf(context, CTX_EXPANSION_APPLIED))
                .transformedQuery((String) context.getOrDefault(CTX_TRANSFORMED_QUERY, pending.redactedQuery()))
                .expandedQueries(stringListOf(context, CTX_EXPANDED_QUERIES))
                .retrievedDocCodes(stringListOf(context, CTX_RETRIEVED_DOC_CODES))
                .retrievedChunkRefs(citationListOf(context, CTX_RETRIEVED_CHUNK_REFS))
                .queryChatClient((String) context.get(CTX_QUERY_CHAT_CLIENT))
                .answerChatClient((String) answerChatClient)
                .responseText(resolveResponseText(response, context))
                .citationStatus((CitationStatus) context.getOrDefault(CTX_CITATION_STATUS, CitationStatus.NOT_APPLICABLE))
                .escalationStatus(booleanOf(context, CTX_ESCALATED))
                .correlationId(pending.correlationId())
                .build();

        auditRepository.save(record);
        logger.info(
                "advisor={} action=turn_persisted sessionId={} turnSeq={} citationStatus={} escalated={}",
                getName(),
                pending.sessionId(),
                pending.turnSeq(),
                record.getCitationStatus(),
                record.isEscalationStatus());

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request, StreamAdvisorChain advisorChain) {
        ChatClientRequest auditedRequest = before(request, advisorChain);
        return advisorChain.nextStream(auditedRequest)
                .collectList()
                .flatMapMany(responses -> {
                    if (responses.isEmpty()) {
                        return Flux.empty();
                    }
                    StringBuilder answer = new StringBuilder();
                    for (ChatClientResponse response : responses) {
                        if (response.chatResponse() != null
                                && response.chatResponse().getResult() != null) {
                            String text = response.chatResponse().getResult().getOutput().getText();
                            if (text != null) {
                                answer.append(text);
                            }
                        }
                    }
                    ChatClientResponse completedResponse = ChatClientResponse.builder()
                            .chatResponse(new ChatResponse(List.of(new Generation(
                                    new AssistantMessage(answer.toString()), ChatGenerationMetadata.NULL))))
                            .context(responses.get(responses.size() - 1).context())
                            .build();
                    after(completedResponse, advisorChain);
                    return Flux.fromIterable(responses);
                });
    }

    @Override
    public String getName() {
        return ConversationAuditAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String resolveResponseText(ChatClientResponse response, Map<String, Object> context) {
        Object override = context.get(CTX_RESPONSE_TEXT_OVERRIDE);
        if (override != null) {
            return RedactionPatterns.redact(override.toString());
        }
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            return RedactionPatterns.redact(response.chatResponse().getResult().getOutput().getText());
        }
        throw new IllegalStateException(
                "No model response available to audit; set " + CTX_RESPONSE_TEXT_OVERRIDE
                        + " for non-model responses (e.g. the fixed empty-context escalation text)");
    }

    private UUID toSessionId(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private boolean booleanOf(Map<String, Object> context, String key) {
        return Boolean.TRUE.equals(context.get(key));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListOf(Map<String, Object> context, String key) {
        return (List<String>) context.getOrDefault(key, List.of());
    }

    @SuppressWarnings("unchecked")
    private List<SourceCitation> citationListOf(Map<String, Object> context, String key) {
        return (List<SourceCitation>) context.getOrDefault(key, List.of());
    }

    private record PendingAudit(
            UUID sessionId,
            int turnSeq,
            UserRole actorRole,
            Instant timestamp,
            String redactedQuery,
            UUID correlationId) {
    }

}
