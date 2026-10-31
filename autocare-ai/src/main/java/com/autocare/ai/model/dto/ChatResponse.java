package com.autocare.ai.model.dto;

import com.autocare.ai.model.enums.CitationStatus;
import java.time.Instant;
import java.util.List;

/**
 * Response payload for {@code POST /ai/autocare/chat/sync} / {@code /ai/autocare/chat/stream}, per
 * architecture.md §15.2 / §15.3.
 *
 * <p>{@code originalQuery} and {@code transformedQuery} echo the pre- and post-transform query text
 * (independently of the {@code ConversationAuditRecord} columns) so API callers can audit exactly what
 * was asked versus what was ultimately sent for retrieval/generation.
 */
public record ChatResponse(
        String sessionId,
        String answer,
        List<SourceCitation> citations,
        List<String> transformationsApplied,
        List<String> retrievedDocCodes,
        boolean escalated,
        boolean citationValidated,
        CitationStatus citationStatus,
        String chatClient,
        String originalQuery,
        String transformedQuery,
        String correlationId,
        Instant timestamp,
        String mode,
        List<String> advisorsUsed,
        long latencyMillis,
        int memorySizeUsed) {

    public ChatResponse(
            String sessionId,
            String answer,
            List<SourceCitation> citations,
            List<String> transformationsApplied,
            List<String> retrievedDocCodes,
            boolean escalated,
            boolean citationValidated,
            CitationStatus citationStatus,
            String chatClient,
            String originalQuery,
            String transformedQuery,
            String correlationId,
            Instant timestamp) {
        this(
                sessionId,
                answer,
                citations,
                transformationsApplied,
                retrievedDocCodes,
                escalated,
                citationValidated,
                citationStatus,
                chatClient,
                originalQuery,
                transformedQuery,
                correlationId,
                timestamp,
                "SYNC",
                List.of(),
                0,
                0);
    }
}
