package com.autocare.ai.service;

import com.azure.core.exception.AzureException;
import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.ChatRequest;
import com.autocare.ai.model.dto.ChatResponse;
import com.autocare.ai.model.dto.KbIngestRequest;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.retrieval.KbDocumentChunkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KbVersionManagementService {

    private static final String AUDIT_CLIENT = "KB_ADMIN_WORKFLOW";

    private final KbDocumentChunkRepository chunkRepository;
    private final ConversationAuditRepository auditRepository;
    private final KbIngestionService ingestionService;
    private final KbValidationQuestionCatalog questionCatalog;
    private final ChatService chatService;

    public KbVersionManagementService(
            KbDocumentChunkRepository chunkRepository,
            ConversationAuditRepository auditRepository,
            KbIngestionService ingestionService,
            KbValidationQuestionCatalog questionCatalog,
            ChatService chatService) {
        this.chunkRepository = chunkRepository;
        this.auditRepository = auditRepository;
        this.ingestionService = ingestionService;
        this.questionCatalog = questionCatalog;
        this.chatService = chatService;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> listDocuments() {
        List<KbDocumentChunkEntity> chunks = chunkRepository.findAll();
        List<DocumentSummary> result = new ArrayList<>();
        chunks.stream().map(KbDocumentChunkEntity::getDocCode).distinct().sorted().forEach(docCode -> {
            List<KbDocumentChunkEntity> documentChunks =
                    chunks.stream().filter(chunk -> chunk.getDocCode().equals(docCode)).toList();
            documentChunks.stream().map(KbDocumentChunkEntity::getVersion).distinct().sorted().forEach(version -> {
                List<KbDocumentChunkEntity> versionChunks =
                        documentChunks.stream().filter(chunk -> chunk.getVersion().equals(version)).toList();
                KbDocumentChunkEntity first = versionChunks.get(0);
                result.add(new DocumentSummary(
                        docCode,
                        version,
                        first.getStatus(),
                        first.getEffectiveDate(),
                        versionChunks.size(),
                        first.getAudience(),
                        first.getVehicleFamily()));
            });
        });
        return List.copyOf(result);
    }

    @Transactional
    public IngestionSummary ingest(KbIngestRequest request) {
        if (request.status() != DocStatus.RETIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingested versions must start as RETIRED");
        }
        List<KbDocumentChunkEntity> saved = ingestionService.ingest(request);
        return new IngestionSummary(
                request.docCode(), request.version(), DocStatus.RETIRED.name(), saved.size());
    }

    public ValidationSummary validate(String docCode, String version, String actor) {
        requireVersion(docCode, version, DocStatus.RETIRED);
        List<KbValidationQuestionCatalog.ValidationQuestion> questions = questionCatalog.forDocument(docCode);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No SRS validation questions exist for this document");
        }

        List<QuestionResult> results = new ArrayList<>();
        try {
            for (KbValidationQuestionCatalog.ValidationQuestion question : questions) {
                ChatRequest request = new ChatRequest(
                        UUID.randomUUID().toString(),
                        question.role(),
                        "all",
                        question.question(),
                        "en");
                ChatResponse response = chatService.chat(request, DocStatus.RETIRED, version);
                boolean passed;
                if (question.audienceCheck()) {
                    passed = response.escalated()
                            && response.retrievedDocCodes().stream().noneMatch(docCode::equals);
                } else {
                    passed = !response.escalated()
                            && response.citationValidated()
                            && response.citations().stream().anyMatch(citation ->
                                    citation.docCode().equals(docCode)
                                            && question.expectedSections().contains(citation.section()));
                }
                results.add(new QuestionResult(
                        question.number(),
                        question.question(),
                        question.expectedSections(),
                        question.expectedBehavior(),
                        passed,
                        response.retrievedDocCodes()));
            }
        } catch (AzureException exception) {
            recordAudit(
                    "KB_VERSION_VALIDATION|" + docCode + "|" + version + "|",
                    actor,
                    "passed=false;serviceUnavailable=true;completedQuestions=" + results.size());
            throw exception;
        }

        boolean passed = results.stream().allMatch(QuestionResult::passed);
        recordAudit(
                "KB_VERSION_VALIDATION|" + docCode + "|" + version + "|",
                actor,
                "passed=" + passed + ";questions=" + results.size() + ";results=" + results);
        return new ValidationSummary(docCode, version, passed, results);
    }

    @Transactional
    public ActivationSummary activate(String docCode, String version, String actor) {
        requireVersion(docCode, version, DocStatus.RETIRED);
        List<ConversationAuditRecord> validations = auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                "KB_VERSION_VALIDATION|" + docCode + "|" + version + "|");
        if (validations.isEmpty() || !validations.get(0).getResponseText().startsWith("passed=true;")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Version activation requires a passing validation for this exact version");
        }

        List<KbDocumentChunkEntity> active = chunkRepository.findByDocCodeAndStatus(docCode, DocStatus.ACTIVE);
        String previousVersion = active.stream()
                .map(KbDocumentChunkEntity::getVersion)
                .distinct()
                .findFirst()
                .orElse("");
        for (String oldVersion : active.stream().map(KbDocumentChunkEntity::getVersion).distinct().toList()) {
            ingestionService.changeVersionStatus(docCode, oldVersion, DocStatus.SUPERSEDED);
        }
        ingestionService.changeVersionStatus(docCode, version, DocStatus.ACTIVE);
        recordAudit(
                "KB_VERSION_ACTIVATED|" + docCode + "|" + version + "|" + previousVersion + "|",
                actor,
                "Activated " + version + "; previous=" + previousVersion);
        return new ActivationSummary(docCode, version, previousVersion, actor, Instant.now());
    }

    @Transactional
    public ActivationSummary rollback(String docCode, String actor) {
        List<ConversationAuditRecord> activations = auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                "KB_VERSION_ACTIVATED|" + docCode + "|");
        if (activations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No previous activation is available to roll back");
        }
        String[] parts = activations.get(0).getOriginalQuery().split("\\|", -1);
        if (parts.length < 4 || parts[3].isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No previous version is available to roll back");
        }
        String activatedVersion = parts[2];
        String previousVersion = parts[3];
        requireVersion(docCode, activatedVersion, DocStatus.ACTIVE);
        requireVersion(docCode, previousVersion, DocStatus.SUPERSEDED);
        ingestionService.changeVersionStatus(docCode, activatedVersion, DocStatus.SUPERSEDED);
        ingestionService.changeVersionStatus(docCode, previousVersion, DocStatus.ACTIVE);
        recordAudit(
                "KB_VERSION_ROLLBACK|" + docCode + "|" + activatedVersion + "|" + previousVersion + "|",
                actor,
                "Rolled back from " + activatedVersion + " to " + previousVersion);
        return new ActivationSummary(docCode, previousVersion, activatedVersion, actor, Instant.now());
    }

    private List<KbDocumentChunkEntity> requireVersion(String docCode, String version, DocStatus status) {
        List<KbDocumentChunkEntity> chunks = chunkRepository.findByDocCodeAndVersion(docCode, version);
        if (chunks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KB document version was not found");
        }
        if (chunks.stream().anyMatch(chunk -> chunk.getStatus() != status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "KB document version must have status " + status);
        }
        if (chunks.stream().anyMatch(chunk -> chunk.getEffectiveDate().isAfter(LocalDate.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KB document version is not yet effective");
        }
        return chunks;
    }

    private void recordAudit(String event, String actor, String response) {
        UUID sessionId = UUID.randomUUID();
        auditRepository.save(ConversationAuditRecord.builder()
                .sessionId(sessionId)
                .turnSeq(1)
                .actorRole(UserRole.ADMIN)
                .timestamp(Instant.now())
                .originalQuery(event)
                .redactedQuery(event)
                .transformedQuery(event)
                .expandedQueries(List.of())
                .retrievedDocCodes(List.of())
                .retrievedChunkRefs(List.of())
                .queryChatClient(actor)
                .answerChatClient(AUDIT_CLIENT)
                .responseText(response)
                .citationStatus(CitationStatus.NOT_APPLICABLE)
                .escalationStatus(false)
                .correlationId(UUID.randomUUID())
                .build());
    }

    public record DocumentSummary(
            String docCode,
            String version,
            DocStatus status,
            LocalDate effectiveDate,
            int chunkCount,
            String audience,
            String vehicleFamily) {
    }

    public record IngestionSummary(String docCode, String version, String status, int chunkCount) {
    }

    public record QuestionResult(
            int number,
            String question,
            List<String> expectedSections,
            String expectedBehavior,
            boolean passed,
            List<String> retrievedDocCodes) {
    }

    public record ValidationSummary(String docCode, String version, boolean passed, List<QuestionResult> results) {
    }

    public record ActivationSummary(
            String docCode, String activeVersion, String previousVersion, String actor, Instant timestamp) {
    }
}
