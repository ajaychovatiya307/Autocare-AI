package com.autocare.ai.controller;

import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai/autocare/monitoring")
@Tag(name = "Monitoring", description = "Admin-only access to redacted conversation audit summaries")
public class MonitoringController {

    private final ConversationAuditRepository auditRepository;

    public MonitoringController(ConversationAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/{correlationId}")
    @Operation(summary = "Get one redacted audit summary by correlation ID (ADMIN only)")
    public MonitoringTurn getByCorrelationId(@PathVariable UUID correlationId) {
        return auditRepository.findByCorrelationId(correlationId).stream()
                .findFirst()
                .map(MonitoringTurn::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitoring turn not found"));
    }

    @GetMapping
    @Operation(summary = "Get redacted audit summaries (ADMIN only); optionally filter by session")
    public List<MonitoringTurn> getTurns(@RequestParam(required = false) UUID sessionId) {
        List<ConversationAuditRecord> records = sessionId == null
                ? auditRepository.findAllByOrderByTimestampDesc()
                : auditRepository.findBySessionIdOrderByTurnSeqAsc(sessionId);
        return records.stream()
                .map(MonitoringTurn::from)
                .toList();
    }

    public record MonitoringTurn(
            String correlationId,
            String sessionId,
            String actor,
            String redactedQuery,
            String citationStatus,
            boolean escalated,
            String timestamp,
            List<TraceStep> steps) {

        static MonitoringTurn from(ConversationAuditRecord record) {
            List<String> documentCodes = record.getRetrievedDocCodes() == null
                    ? List.of()
                    : record.getRetrievedDocCodes();
            List<SourceCitation> citations = record.getRetrievedChunkRefs() == null
                    ? List.of()
                    : record.getRetrievedChunkRefs();
            boolean noDocuments = documentCodes.isEmpty() && citations.isEmpty();
            boolean escalated = noDocuments || record.isEscalationStatus();
            String citationStatus = noDocuments
                    ? "FALLBACK"
                    : record.getCitationStatus() == CitationStatus.VALID ? "PASS"
                    : record.getCitationStatus() == CitationStatus.BLOCKED ? "FAIL" : "FALLBACK";
            long turnDurationMs = record.getCreatedAt() == null
                    ? 0
                    : Math.max(0, Duration.between(record.getTimestamp(), record.getCreatedAt()).toMillis());

            return new MonitoringTurn(
                    record.getCorrelationId().toString(),
                    record.getSessionId().toString(),
                    record.getActorRole().name(),
                    record.getRedactedQuery(),
                    citationStatus,
                    escalated,
                    record.getTimestamp().toString(),
                    steps(record, citations, noDocuments, escalated, citationStatus, turnDurationMs));
        }

        private static List<TraceStep> steps(
                ConversationAuditRecord record,
                List<SourceCitation> citations,
                boolean noDocuments,
                boolean escalated,
                String citationStatus,
                long turnDurationMs) {
            List<TraceStep> steps = new java.util.ArrayList<>();
            add(steps, "PiiVehicleRedactionAdvisor", "CUSTOM_ADVISOR", "APPLIED",
                    "Sensitive values were redacted before processing.", 0);
            add(steps, "ConversationAuditAdvisor (start)", "CUSTOM_ADVISOR", "APPLIED",
                    "Turn started actor=" + record.getActorRole().name(), 0);
            add(steps, "SimpleLoggerAdvisor", "FRAMEWORK_ADVISOR", "APPLIED",
                    "Framework diagnostic logging; not the audit store.", 0);

            add(steps, "TranslationQueryTransformer", "RAG_MODULE",
                    record.isTranslationApplied() ? "APPLIED" : "SKIPPED",
                    record.isTranslationApplied()
                            ? "Translated query for retrieval."
                            : "Translation was not applied; language=" + safeLanguage(record.getLanguageDetected()),
                    0);
            add(steps, "RewriteQueryTransformer", "RAG_MODULE",
                    record.isRewriteApplied() ? "APPLIED" : "SKIPPED",
                    record.isRewriteApplied() ? record.getTransformedQuery() : "No context-dependent rewrite was needed.",
                    0);

            List<String> variants = record.getExpandedQueries() == null ? List.of() : record.getExpandedQueries();
            add(steps, "MultiQueryExpander", "RAG_MODULE",
                    record.isExpansionApplied() ? "APPLIED" : "SKIPPED",
                    record.isExpansionApplied() ? variants.size() + " variants" : "Query expansion was not needed.",
                    0);

            if (noDocuments) {
                add(steps, "VectorStoreDocumentRetriever", "RAG_MODULE", "FALLBACK", "0 docs ≥ 0.50", 0);
            } else {
                String docs = citations.stream()
                        .map(citation -> citation.docCode() + " §" + citation.section() + " "
                                + citation.version() + " (score="
                                + (citation.score() == null ? "unavailable" : String.format("%.2f", citation.score()))
                                + ")")
                        .distinct()
                        .collect(java.util.stream.Collectors.joining(", "));
                add(steps, "VectorStoreDocumentRetriever", "RAG_MODULE", "APPLIED", docs, 0);
            }
            add(steps, "ContextualQueryAugmenter", "RAG_MODULE",
                    noDocuments ? "FALLBACK" : "APPLIED",
                    noDocuments ? "empty context (allowEmptyContext=false)" : citations.size() + " docs injected",
                    0);

            if (record.getQueryChatClient() != null && !record.getQueryChatClient().isBlank()) {
                add(steps, record.getQueryChatClient(), "CHAT_MODEL", "APPLIED",
                        "stage=query-transform; durationMs=" + turnDurationMs, turnDurationMs);
            }
            add(steps, record.getAnswerChatClient(), "CHAT_MODEL", "APPLIED",
                    "stage=answer; durationMs=" + turnDurationMs, turnDurationMs);
            add(steps, "CitationValidationAdvisor", "CUSTOM_ADVISOR", citationStatus,
                    noDocuments ? "no retrieved sources; SRS fallback" : "Citation validation " + citationStatus,
                    0);
            add(steps, "ConversationAuditAdvisor (saved, escalated=" + escalated + ")",
                    "CUSTOM_ADVISOR", "APPLIED", "Redacted audit row persisted.", 0);
            return List.copyOf(steps);
        }

        private static void add(
                List<TraceStep> steps, String component, String type, String status, String detail, long durationMs) {
            steps.add(new TraceStep(
                    steps.size() + 1, component, type, status, detail, durationMs));
        }

        private static String safeLanguage(String language) {
            return language == null || language.isBlank() ? "unknown" : language;
        }
    }

    public record TraceStep(
            int seq,
            String component,
            String type,
            String status,
            String detail,
            long durationMs) {
    }
}
