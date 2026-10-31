package com.autocare.ai.advisor;

import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;

/**
 * Advisor chain stage 12 (architecture.md §17.12): synchronously validates that the candidate answer
 * is backed by retrieved KB sources before it can be returned (NFR-001). Reads the documents {@code
 * RetrievalAugmentationAdvisor} (T8.8) actually retrieved, via its {@link
 * RetrievalAugmentationAdvisor#DOCUMENT_CONTEXT} context key, and blocks the answer when none were
 * retrieved; otherwise it derives and publishes {@link ConversationAuditAdvisor#CTX_RETRIEVED_DOC_CODES}
 * / {@link ConversationAuditAdvisor#CTX_RETRIEVED_CHUNK_REFS} for the audit record and API response.
 * Ordered closest to the model call so its {@code after()} runs before {@link DomainSafeguardAdvisor}'s.
 */
@Component
public class CitationValidationAdvisor implements BaseAdvisor {

    public static final int ORDER = DomainSafeguardAdvisor.ORDER + 100;
    private static final Logger logger = LoggerFactory.getLogger(CitationValidationAdvisor.class);

    static final String UNSUPPORTED_CLAIM_FALLBACK =
            "AutoCare AI could not verify this answer against retrieved AutoCare Knowledge Base content, so it "
                    + "has been blocked. Please contact an authorized service advisor.";
    private static final Pattern NO_KNOWLEDGE_ANSWER =
            Pattern.compile("(?i)^(?:i (?:do not|don't) know\\.?|i cannot find (?:that|this) information\\.?)$");
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            logger.debug("advisor={} action=skipped reason=no_chat_response", getName());
            return response;
        }
        String answerText = response.chatResponse().getResult().getOutput().getText();
        if (answerText == null || answerText.isBlank()) {
            logger.debug("advisor={} action=skipped reason=empty_answer", getName());
            return response;
        }

        if (NoMatchingContextException.ESCALATION_MESSAGE.equals(answerText.trim())) {
            logger.info("advisor={} action=validation_skipped reason=controlled_escalation", getName());
            return response.mutate()
                    .context(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.NOT_APPLICABLE)
                    .context(ConversationAuditAdvisor.CTX_ESCALATED, true)
                    .context(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of())
                    .context(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, List.of())
                    .build();
        }

        if (NO_KNOWLEDGE_ANSWER.matcher(answerText.trim()).matches()) {
            logger.info("advisor={} action=validation_skipped reason=no_knowledge_answer", getName());
            return response.mutate()
                    .chatResponse(blockedChatResponse(NoMatchingContextException.ESCALATION_MESSAGE))
                    .context(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.NOT_APPLICABLE)
                    .context(ConversationAuditAdvisor.CTX_ESCALATED, true)
                    .context(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of())
                    .context(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, List.of())
                    .build();
        }

        List<Document> retrieved = retrievedDocuments(response.context());
        if (retrieved.isEmpty()) {
            logger.info("advisor={} action=answer_blocked reason=no_retrieved_sources", getName());
            return response.mutate()
                    .chatResponse(blockedChatResponse())
                    .context(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.BLOCKED)
                    .context(ConversationAuditAdvisor.CTX_ESCALATED, true)
                    .build();
        }

        logger.info("advisor={} action=citations_validated retrievedSourceCount={}", getName(), retrieved.size());
        return response.mutate()
                .context(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.VALID)
                .context(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, docCodesOf(retrieved))
                .context(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, citationsOf(retrieved))
                .build();
    }

    @Override
    public String getName() {
        return CitationValidationAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @SuppressWarnings("unchecked")
    static List<Document> retrievedDocuments(Map<String, Object> context) {
        Object documents = context.get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return documents instanceof List<?> list ? (List<Document>) list : List.of();
    }

    static boolean hasRetrievedEvidence(Map<String, Object> context) {
        return !retrievedDocuments(context).isEmpty();
    }

    static List<String> docCodesOf(List<Document> documents) {
        return documents.stream()
                .map(document -> (String) document.getMetadata().get("doc_code"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    static List<SourceCitation> citationsOf(List<Document> documents) {
        return documents.stream()
                .map(document -> new SourceCitation(
                        (String) document.getMetadata().get("doc_code"),
                        (String) document.getMetadata().get("version"),
                        String.valueOf(document.getMetadata().getOrDefault("section", "")),
                        document.getScore()))
                .toList();
    }

    static ChatResponse blockedChatResponse() {
        return blockedChatResponse(UNSUPPORTED_CLAIM_FALLBACK);
    }

    static ChatResponse blockedChatResponse(String message) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(message), ChatGenerationMetadata.NULL)));
    }
}
