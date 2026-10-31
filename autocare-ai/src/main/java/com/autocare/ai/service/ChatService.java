package com.autocare.ai.service;

import com.autocare.ai.advisor.CitationValidationAdvisor;
import com.autocare.ai.advisor.ConversationAuditAdvisor;
import com.autocare.ai.advisor.DomainSafeguardAdvisor;
import com.autocare.ai.advisor.PiiVehicleRedactionAdvisor;
import com.autocare.ai.advisor.StreamingCitationValidationAdvisor;
import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.config.RetrievalAugmentationConfig;
import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.dto.ChatRequest;
import com.autocare.ai.model.dto.ChatResponse;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.MetadataFilterBuilder;
import com.autocare.ai.security.RedactionPatterns;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Orchestrates one chat turn through the fixed advisor/RAG pipeline (SRS §2.1, architecture.md §17):
 * redaction → audit open → diagnostic logging → retrieval augmentation (language detection, conditional
 * transformers, retriever, augmenter) → {@code AnswerChatClient} → citation validation → domain
 * safeguards → audit finalize. Stage order is enforced by each advisor's {@code getOrder()}, not by the
 * order they are passed here.
 */
@Service
public class ChatService {

    public static final String ANSWER_CHAT_CLIENT = "AnswerChatClient";
    private static final String ASYNC_MODE = "ASYNC";
    private static final String SYNC_MODE = "SYNC";
    private final ChatClient answerChatClient;
    private final ConversationAuditRepository auditRepository;
    private final MetadataFilterBuilder metadataFilterBuilder;
    private final RetrievalAugmentationConfig retrievalAugmentationConfig;
    private final PiiVehicleRedactionAdvisor redactionAdvisor;
    private final ConversationAuditAdvisor auditAdvisor;
    private final SimpleLoggerAdvisor loggerAdvisor;
    private final CitationValidationAdvisor citationValidationAdvisor;
    private final StreamingCitationValidationAdvisor streamingCitationValidationAdvisor;
    private final DomainSafeguardAdvisor domainSafeguardAdvisor;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final ChatMemory chatMemory;
    private final PromptTemplate answerPromptTemplate;

    public ChatService(
            @Qualifier("answerChatClient") ChatClient answerChatClient,
            ConversationAuditRepository auditRepository,
            MetadataFilterBuilder metadataFilterBuilder,
            RetrievalAugmentationConfig retrievalAugmentationConfig,
            PiiVehicleRedactionAdvisor redactionAdvisor,
            ConversationAuditAdvisor auditAdvisor,
            SimpleLoggerAdvisor loggerAdvisor,
            CitationValidationAdvisor citationValidationAdvisor,
            StreamingCitationValidationAdvisor streamingCitationValidationAdvisor,
            DomainSafeguardAdvisor domainSafeguardAdvisor,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
            ChatMemory chatMemory,
            @Qualifier("answerPromptTemplate") PromptTemplate answerPromptTemplate) {
        this.answerChatClient = answerChatClient;
        this.auditRepository = auditRepository;
        this.metadataFilterBuilder = metadataFilterBuilder;
        this.retrievalAugmentationConfig = retrievalAugmentationConfig;
        this.redactionAdvisor = redactionAdvisor;
        this.auditAdvisor = auditAdvisor;
        this.loggerAdvisor = loggerAdvisor;
        this.citationValidationAdvisor = citationValidationAdvisor;
        this.streamingCitationValidationAdvisor = streamingCitationValidationAdvisor;
        this.domainSafeguardAdvisor = domainSafeguardAdvisor;
        this.messageChatMemoryAdvisor = messageChatMemoryAdvisor;
        this.chatMemory = chatMemory;
        this.answerPromptTemplate = answerPromptTemplate;
    }

    public ChatResponse chat(ChatRequest request) {
        return chat(request, DocStatus.ACTIVE, null);
    }

    public ChatResponse chat(ChatRequest request, DocStatus status, String version) {
        long startedAt = System.nanoTime();
        UUID correlationId = UUID.randomUUID();
        int memorySize = chatMemory.get(request.sessionId()).size();
        if (metadataFilterBuilder.needsVehicleFamilyClarification(request.vehicleFamily())) {
            return trace(clarificationResponse(request, correlationId), SYNC_MODE, startedAt, memorySize);
        }
        try {
            ChatClientResponse response = invoke(request, correlationId, false, status, version)
                    .call()
                    .chatClientResponse();
            return trace(toChatResponse(request, response, correlationId), SYNC_MODE, startedAt, memorySize);
        } catch (NoMatchingContextException exception) {
            recordNoMatchingContext(request, correlationId);
            throw exception;
        }
    }

    public Flux<ChatResponse> streamChat(ChatRequest request) {
        long startedAt = System.nanoTime();
        UUID correlationId = UUID.randomUUID();
        int memorySize = chatMemory.get(request.sessionId()).size();
        if (metadataFilterBuilder.needsVehicleFamilyClarification(request.vehicleFamily())) {
            return Flux.just(trace(clarificationResponse(request, correlationId), ASYNC_MODE, startedAt, memorySize));
        }
        Flux<ChatClientResponse> responses = invoke(request, correlationId, true, DocStatus.ACTIVE, null)
                .stream()
                .chatClientResponse()
                .doOnError(exception -> {
                    if (hasNoMatchingContextCause(exception)) {
                        recordNoMatchingContext(request, correlationId);
                    }
                });
        return responses.map(response -> trace(
                toChatResponse(request, response, correlationId), ASYNC_MODE, startedAt, memorySize));
    }

    private boolean hasNoMatchingContextCause(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof NoMatchingContextException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ChatClient.ChatClientRequestSpec invoke(
            ChatRequest request, UUID correlationId, boolean streaming, DocStatus status, String version) {
        MetadataFilterBuilder.Filter filter =
                metadataFilterBuilder.build(request.role(), request.vehicleFamily(), status, version);
        List<Advisor> advisors = advisorsFor(filter, streaming);

        return answerChatClient
                .prompt()
                .system(answerPromptTemplate.render(Map.of(
                        "role",
                        request.role().name(),
                        "language",
                        languageName(request.language()))))
                .user(request.query())
                .advisors(spec -> spec.advisors(advisors)
                        .param(ConversationAuditAdvisor.CTX_SESSION_ID, request.sessionId())
                        .param(ConversationAuditAdvisor.CTX_ACTOR_ROLE, request.role())
                        .param(ConversationAuditAdvisor.CTX_CORRELATION_ID, correlationId.toString())
                        .param(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, ANSWER_CHAT_CLIENT)
                        .param(ChatMemory.CONVERSATION_ID, request.sessionId()));
    }

    private List<Advisor> advisorsFor(MetadataFilterBuilder.Filter filter, boolean streaming) {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(redactionAdvisor);
        advisors.add(messageChatMemoryAdvisor);
        advisors.add(auditAdvisor);
        advisors.add(loggerAdvisor);
        advisors.add(retrievalAugmentationConfig.advisorFor(filter));
        // Streaming must buffer the whole answer before any chunk is released (NFR-001 for /stream).
        advisors.add(streaming ? streamingCitationValidationAdvisor : citationValidationAdvisor);
        advisors.add(domainSafeguardAdvisor);
        return advisors;
    }

    private ChatResponse toChatResponse(ChatRequest request, ChatClientResponse response, UUID correlationId) {
        Map<String, Object> context = response.context();
        String redactedOriginalQuery = RedactionPatterns.redact(request.query());
        String answer = response.chatResponse() == null || response.chatResponse().getResult() == null
                ? ""
                : RedactionPatterns.redact(response.chatResponse().getResult().getOutput().getText());

        CitationStatus citationStatus = (CitationStatus)
                context.getOrDefault(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.NOT_APPLICABLE);
        boolean escalated = Boolean.TRUE.equals(context.get(ConversationAuditAdvisor.CTX_ESCALATED));

        return new ChatResponse(
                request.sessionId(),
                answer,
                citationsOf(context),
                transformationsApplied(context),
                stringListOf(context, ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES),
                escalated,
                citationStatus == CitationStatus.VALID,
                citationStatus,
                ANSWER_CHAT_CLIENT,
                redactedOriginalQuery,
                (String) context.getOrDefault(
                        ConversationAuditAdvisor.CTX_TRANSFORMED_QUERY, redactedOriginalQuery),
                correlationId.toString(),
                Instant.now());
    }

    private ChatResponse trace(ChatResponse response, String mode, long startedAt, int memorySize) {
        return new ChatResponse(
                response.sessionId(),
                response.answer(),
                response.citations(),
                response.transformationsApplied(),
                response.retrievedDocCodes(),
                response.escalated(),
                response.citationValidated(),
                response.citationStatus(),
                response.chatClient(),
                response.originalQuery(),
                response.transformedQuery(),
                response.correlationId(),
                response.timestamp(),
                mode,
                advisorNames(mode.equals(ASYNC_MODE)),
                (System.nanoTime() - startedAt) / 1_000_000,
                memorySize);
    }

    private List<String> advisorNames(boolean streaming) {
        return List.of(
                PiiVehicleRedactionAdvisor.class.getSimpleName(),
                MessageChatMemoryAdvisor.class.getSimpleName(),
                ConversationAuditAdvisor.class.getSimpleName(),
                SimpleLoggerAdvisor.class.getSimpleName(),
                "RetrievalAugmentationAdvisor",
                streaming
                        ? StreamingCitationValidationAdvisor.class.getSimpleName()
                        : CitationValidationAdvisor.class.getSimpleName(),
                DomainSafeguardAdvisor.class.getSimpleName());
    }

    private String languageName(String language) {
        if (language == null || language.isBlank()) {
            return "the language used by the user query";
        }
        String displayName = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH);
        return displayName.isBlank() ? "the language used by the user query" : displayName;
    }

    private void recordNoMatchingContext(ChatRequest request, UUID correlationId) {
        String redactedQuery = RedactionPatterns.redact(request.query());
        recordControlledTurn(
                request,
                correlationId,
                redactedQuery,
                NoMatchingContextException.ESCALATION_MESSAGE,
                CitationStatus.NOT_APPLICABLE,
                true);
    }

    private ChatResponse clarificationResponse(ChatRequest request, UUID correlationId) {
        String redactedQuery = RedactionPatterns.redact(request.query());
        String answer = "Please provide an unambiguous supported vehicle-family code, such as AC-SD-P or AC-EV-1.";
        recordControlledTurn(
                request,
                correlationId,
                redactedQuery,
                answer,
                CitationStatus.NOT_APPLICABLE,
                false);
        return new ChatResponse(
                request.sessionId(),
                answer,
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                CitationStatus.NOT_APPLICABLE,
                ANSWER_CHAT_CLIENT,
                redactedQuery,
                redactedQuery,
                correlationId.toString(),
                Instant.now());
    }

    private void recordControlledTurn(
            ChatRequest request,
            UUID correlationId,
            String redactedQuery,
            String answer,
            CitationStatus citationStatus,
            boolean escalated) {
        UUID sessionId = UUID.fromString(request.sessionId());
        auditRepository.save(ConversationAuditRecord.builder()
                .sessionId(sessionId)
                .turnSeq((int) auditRepository.countBySessionId(sessionId) + 1)
                .actorRole(request.role())
                .timestamp(Instant.now())
                .originalQuery(redactedQuery)
                .redactedQuery(redactedQuery)
                .transformedQuery(redactedQuery)
                .expandedQueries(List.of())
                .retrievedDocCodes(List.of())
                .retrievedChunkRefs(List.of())
                .answerChatClient(ANSWER_CHAT_CLIENT)
                .responseText(answer)
                .citationStatus(citationStatus)
                .escalationStatus(escalated)
                .correlationId(correlationId)
                .build());
    }

    private List<String> transformationsApplied(Map<String, Object> context) {
        List<String> applied = new ArrayList<>();
        if (Boolean.TRUE.equals(context.get(ConversationAuditAdvisor.CTX_TRANSLATION_APPLIED))) {
            applied.add("TRANSLATE");
        }
        if (Boolean.TRUE.equals(context.get(ConversationAuditAdvisor.CTX_REWRITE_APPLIED))) {
            applied.add("REWRITE");
        }
        if (Boolean.TRUE.equals(context.get(ConversationAuditAdvisor.CTX_EXPANSION_APPLIED))) {
            applied.add("EXPAND");
        }
        return applied;
    }

    @SuppressWarnings("unchecked")
    private List<SourceCitation> citationsOf(Map<String, Object> context) {
        return (List<SourceCitation>)
                context.getOrDefault(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, List.of());
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListOf(Map<String, Object> context, String key) {
        return (List<String>) context.getOrDefault(key, List.of());
    }
}
