package com.autocare.ai.advisor;

import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.enums.CitationStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Streaming counterpart of {@link CitationValidationAdvisor} (T9.4) for {@code POST
 * /ai/autocare/chat/stream} (NFR-001 applied to streaming). Unlike a plain {@code BaseAdvisor}, which
 * would validate each emitted chunk independently, this advisor buffers the entire upstream response
 * via {@link Flux#collectList()} and only validates the fully assembled answer, so no partial answer
 * chunk is ever released to the caller before citation validation has run.
 */
@Component
public class StreamingCitationValidationAdvisor implements StreamAdvisor {

    public static final int ORDER = CitationValidationAdvisor.ORDER;
    private static final Logger logger = LoggerFactory.getLogger(StreamingCitationValidationAdvisor.class);

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain advisorChain) {
        logger.info("advisor={} action=stream_validation_started", getName());
        return advisorChain.nextStream(request).collectList().flatMapMany(this::validateBuffered);
    }

    @Override
    public String getName() {
        return StreamingCitationValidationAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private Flux<ChatClientResponse> validateBuffered(List<ChatClientResponse> chunks) {
        if (chunks.isEmpty()) {
            logger.debug("advisor={} action=stream_validation_skipped reason=no_chunks", getName());
            return Flux.empty();
        }

        Map<String, Object> lastContext = chunks.get(chunks.size() - 1).context();
        String aggregatedText = aggregateText(chunks);

        if (NoMatchingContextException.ESCALATION_MESSAGE.equals(aggregatedText.trim())
                || aggregatedText.matches(
                        "(?i)^(?:i (?:do not|don't) know\\.?|i cannot find (?:that|this) information\\.?)$")) {
            logger.info(
                    "advisor={} action=stream_answer_blocked reason=controlled_escalation chunkCount={}",
                    getName(),
                    chunks.size());
            return Flux.just(ChatClientResponse.builder()
                    .chatResponse(CitationValidationAdvisor.blockedChatResponse(
                            NoMatchingContextException.ESCALATION_MESSAGE))
                    .context(mergedContext(lastContext, Map.of(
                            ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.NOT_APPLICABLE,
                            ConversationAuditAdvisor.CTX_ESCALATED, true,
                            ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of(),
                            ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, List.of())))
                    .build());
        }

        if (!CitationValidationAdvisor.hasRetrievedEvidence(lastContext)) {
            logger.info(
                    "advisor={} action=stream_answer_blocked reason=no_retrieved_sources chunkCount={}",
                    getName(),
                    chunks.size());
            return Flux.just(ChatClientResponse.builder()
                    .chatResponse(CitationValidationAdvisor.blockedChatResponse())
                    .context(mergedContext(lastContext, Map.of(
                            ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.BLOCKED,
                            ConversationAuditAdvisor.CTX_ESCALATED, true)))
                    .build());
        }

        List<Document> retrieved = CitationValidationAdvisor.retrievedDocuments(lastContext);
        logger.info(
                "advisor={} action=stream_citations_validated chunkCount={} retrievedSourceCount={}",
                getName(),
                chunks.size(),
                retrieved.size());
        Map<String, Object> citationContext = Map.of(
                ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.VALID,
                ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, CitationValidationAdvisor.docCodesOf(retrieved),
                ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, CitationValidationAdvisor.citationsOf(retrieved));
        return Flux.fromIterable(withLastContext(chunks, citationContext));
    }

    private String aggregateText(List<ChatClientResponse> chunks) {
        StringBuilder aggregated = new StringBuilder();
        for (ChatClientResponse chunk : chunks) {
            if (chunk.chatResponse() != null && chunk.chatResponse().getResult() != null) {
                String text = chunk.chatResponse().getResult().getOutput().getText();
                if (text != null) {
                    aggregated.append(text);
                }
            }
        }
        return aggregated.toString();
    }

    private List<ChatClientResponse> withLastContext(List<ChatClientResponse> chunks, Map<String, Object> additions) {
        List<ChatClientResponse> result = new ArrayList<>(chunks);
        int lastIndex = result.size() - 1;
        ChatClientResponse last = result.get(lastIndex);
        result.set(lastIndex, last.mutate().context(mergedContext(last.context(), additions)).build());
        return result;
    }

    private Map<String, Object> mergedContext(Map<String, Object> base, Map<String, Object> additions) {
        Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(additions);
        return merged;
    }
}
