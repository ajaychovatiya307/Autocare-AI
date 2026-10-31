package com.autocare.ai.service;

import com.azure.core.exception.AzureException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.ChatRequest;
import com.autocare.ai.model.dto.ChatResponse;
import com.autocare.ai.model.dto.KbIngestRequest;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.KbDocumentChunkEntity;
import com.autocare.ai.retrieval.KbDocumentChunkRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class KbVersionManagementServiceTest {

    private KbDocumentChunkRepository chunkRepository;
    private ConversationAuditRepository auditRepository;
    private KbIngestionService ingestionService;
    private KbValidationQuestionCatalog questionCatalog;
    private ChatService chatService;
    private KbVersionManagementService service;

    @BeforeEach
    void setUp() {
        chunkRepository = Mockito.mock(KbDocumentChunkRepository.class);
        auditRepository = Mockito.mock(ConversationAuditRepository.class);
        ingestionService = Mockito.mock(KbIngestionService.class);
        questionCatalog = Mockito.mock(KbValidationQuestionCatalog.class);
        chatService = Mockito.mock(ChatService.class);
        service = new KbVersionManagementService(
                chunkRepository, auditRepository, ingestionService, questionCatalog, chatService);
    }

    private KbDocumentChunkEntity chunk(String version, DocStatus status) {
        return KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-001")
                .version(version)
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .status(status)
                .audience("OWNER|ADVISOR")
                .vehicleFamily("all")
                .section("2")
                .content("Supported vehicle family information.")
                .embedding(new float[] {1f})
                .build();
    }

    private ConversationAuditRecord validationRecord(boolean passed) {
        return ConversationAuditRecord.builder()
                .sessionId(UUID.randomUUID())
                .turnSeq(1)
                .actorRole(UserRole.ADMIN)
                .timestamp(Instant.now())
                .originalQuery("KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|")
                .redactedQuery("KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|")
                .transformedQuery("KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|")
                .answerChatClient("KB_ADMIN_WORKFLOW")
                .responseText("passed=" + passed + ";questions=2")
                .citationStatus(CitationStatus.NOT_APPLICABLE)
                .escalationStatus(false)
                .correlationId(UUID.randomUUID())
                .build();
    }

    @Test
    void activationIsBlockedUntilValidationPasses() {
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));

        assertThatThrownBy(() -> service.activate("KB-AUTO-001", "v1.1", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("passing validation");
        verify(ingestionService, never()).changeVersionStatus(anyString(), anyString(), any());
    }

    @Test
    void activationRequiresTheLatestValidationOfTheExactVersion() {
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                        "KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|"))
                .thenReturn(List.of(validationRecord(false), validationRecord(true)));

        assertThatThrownBy(() -> service.activate("KB-AUTO-001", "v1.1", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("passing validation");
        verify(ingestionService, never()).changeVersionStatus(anyString(), anyString(), any());
    }

    @Test
    void activationPassesValidatedCandidateAndSupersedesCurrentVersion() {
        KbDocumentChunkEntity active = chunk("v1.0", DocStatus.ACTIVE);
        KbDocumentChunkEntity candidate = chunk("v1.1", DocStatus.RETIRED);
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1")).thenReturn(List.of(candidate));
        when(chunkRepository.findByDocCodeAndStatus("KB-AUTO-001", DocStatus.ACTIVE)).thenReturn(List.of(active));
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                        "KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|"))
                .thenReturn(List.of(validationRecord(true)));

        KbVersionManagementService.ActivationSummary summary = service.activate("KB-AUTO-001", "v1.1", "admin-user");

        assertThat(summary.activeVersion()).isEqualTo("v1.1");
        assertThat(summary.previousVersion()).isEqualTo("v1.0");
        verify(ingestionService).changeVersionStatus("KB-AUTO-001", "v1.0", DocStatus.SUPERSEDED);
        verify(ingestionService).changeVersionStatus("KB-AUTO-001", "v1.1", DocStatus.ACTIVE);
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void rollbackReactivatesTheVersionRecordedAsPrevious() {
        KbDocumentChunkEntity active = chunk("v1.1", DocStatus.ACTIVE);
        KbDocumentChunkEntity previous = chunk("v1.0", DocStatus.SUPERSEDED);
        ConversationAuditRecord activation = ConversationAuditRecord.builder()
                .originalQuery("KB_VERSION_ACTIVATED|KB-AUTO-001|v1.1|v1.0|")
                .build();
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                        "KB_VERSION_ACTIVATED|KB-AUTO-001|"))
                .thenReturn(List.of(activation));
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1")).thenReturn(List.of(active));
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.0")).thenReturn(List.of(previous));

        KbVersionManagementService.ActivationSummary summary = service.rollback("KB-AUTO-001", "admin-user");

        assertThat(summary.activeVersion()).isEqualTo("v1.0");
        verify(ingestionService).changeVersionStatus("KB-AUTO-001", "v1.1", DocStatus.SUPERSEDED);
        verify(ingestionService).changeVersionStatus("KB-AUTO-001", "v1.0", DocStatus.ACTIVE);
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void failedValidationIsRecordedAndDoesNotPassTheGate() {
        var question = new KbValidationQuestionCatalog.ValidationQuestion(
                "KB-AUTO-001", 1, "Question from the catalog", List.of("2"), "Expected grounding", UserRole.OWNER, false);
        ChatResponse response = new ChatResponse(
                UUID.randomUUID().toString(),
                "No matching vehicle service or warranty information was found.",
                List.of(),
                List.of(),
                List.of(),
                true,
                false,
                CitationStatus.NOT_APPLICABLE,
                "AnswerChatClient",
                "Question",
                "Question",
                UUID.randomUUID().toString(),
                Instant.now());
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(questionCatalog.forDocument("KB-AUTO-001")).thenReturn(List.of(question));
        when(chatService.chat(any(ChatRequest.class), any(DocStatus.class), any())).thenReturn(response);

        KbVersionManagementService.ValidationSummary result = service.validate("KB-AUTO-001", "v1.1", "admin-user");

        assertThat(result.passed()).isFalse();
        assertThat(result.results()).singleElement().satisfies(item -> assertThat(item.passed()).isFalse());
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void listingGroupsChunksByDocumentAndVersion() {
        when(chunkRepository.findAll())
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED), chunk("v1.1", DocStatus.RETIRED)));

        assertThat(service.listDocuments())
                .singleElement()
                .satisfies(version -> {
                    assertThat(version.docCode()).isEqualTo("KB-AUTO-001");
                    assertThat(version.version()).isEqualTo("v1.1");
                    assertThat(version.status()).isEqualTo(DocStatus.RETIRED);
                    assertThat(version.chunkCount()).isEqualTo(2);
                });
    }

    @Test
    void ingestCreatesOnlyRetiredCandidateVersion() {
        KbIngestRequest request = new KbIngestRequest(
                "KB-AUTO-001",
                "v1.1",
                LocalDate.of(2026, 10, 1),
                DocStatus.RETIRED,
                "OWNER|ADVISOR",
                "all",
                "2",
                "vehicle_reference",
                "Candidate document content.",
                UserRole.ADMIN);
        KbDocumentChunkEntity saved = chunk("v1.1", DocStatus.RETIRED);
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1")).thenReturn(List.of());
        when(ingestionService.ingest(request)).thenReturn(List.of(saved));

        assertThat(service.ingest(request))
                .isEqualTo(new KbVersionManagementService.IngestionSummary(
                        "KB-AUTO-001", "v1.1", "RETIRED", 1));
        verify(ingestionService).ingest(request);
    }

    @Test
    void validationRequiresMatchingGroundingAndAudienceExclusion() {
        var groundingQuestion = new KbValidationQuestionCatalog.ValidationQuestion(
                "KB-AUTO-001", 1, "Grounded question", List.of("2"), "Expected answer", UserRole.OWNER, false);
        var audienceQuestion = new KbValidationQuestionCatalog.ValidationQuestion(
                "KB-AUTO-001", 2, "Restricted question", List.of(), "Audience filter", UserRole.OWNER, true);
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(questionCatalog.forDocument("KB-AUTO-001"))
                .thenReturn(List.of(groundingQuestion, audienceQuestion));
        when(chatService.chat(any(ChatRequest.class), any(DocStatus.class), anyString()))
                .thenReturn(
                        response(false, true, List.of("KB-AUTO-001"), List.of(new SourceCitation(
                                "KB-AUTO-001", "v1.1", "2"))),
                        response(true, false, List.of(), List.of()));

        KbVersionManagementService.ValidationSummary result =
                service.validate("KB-AUTO-001", "v1.1", "admin-user");

        assertThat(result.passed()).isTrue();
        assertThat(result.results()).allSatisfy(question -> assertThat(question.passed()).isTrue());
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void rollbackRejectsMissingActivationAndMissingPreviousVersion() {
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc("KB_VERSION_ACTIVATED|KB-AUTO-001|"))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.rollback("KB-AUTO-001", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No previous activation");

        ConversationAuditRecord noPrevious = ConversationAuditRecord.builder()
                .originalQuery("KB_VERSION_ACTIVATED|KB-AUTO-001|v1.1||")
                .build();
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc("KB_VERSION_ACTIVATED|KB-AUTO-001|"))
                .thenReturn(List.of(noPrevious));
        assertThatThrownBy(() -> service.rollback("KB-AUTO-001", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No previous version");
    }

    @Test
    void validationRejectsMissingUnknownStatusAndFutureEffectiveVersions() {
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "missing")).thenReturn(List.of());
        assertThatThrownBy(() -> service.validate("KB-AUTO-001", "missing", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "active"))
                .thenReturn(List.of(chunk("active", DocStatus.ACTIVE)));
        assertThatThrownBy(() -> service.validate("KB-AUTO-001", "active", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("status RETIRED");

        KbDocumentChunkEntity future = KbDocumentChunkEntity.builder()
                .docCode("KB-AUTO-001")
                .version("future")
                .effectiveDate(LocalDate.now().plusDays(1))
                .status(DocStatus.RETIRED)
                .audience("OWNER")
                .vehicleFamily("all")
                .section("2")
                .content("Future content.")
                .embedding(new float[] {1f})
                .build();
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "future")).thenReturn(List.of(future));
        assertThatThrownBy(() -> service.validate("KB-AUTO-001", "future", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not yet effective");
    }

    @Test
    void validationRejectsDocumentsWithoutSrsQuestions() {
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(questionCatalog.forDocument("KB-AUTO-001")).thenReturn(List.of());

        assertThatThrownBy(() -> service.validate("KB-AUTO-001", "v1.1", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No SRS validation questions");
    }

    @Test
    void externalModelFailureIsAuditedAndRemainsAnExplicitError() {
        var question = new KbValidationQuestionCatalog.ValidationQuestion(
                "KB-AUTO-001", 1, "Question from the catalog", List.of("2"), "Expected grounding", UserRole.OWNER, false);
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(questionCatalog.forDocument("KB-AUTO-001")).thenReturn(List.of(question));
        when(chatService.chat(any(ChatRequest.class), any(DocStatus.class), anyString()))
                .thenThrow(new AzureException("model service unavailable"));

        assertThatThrownBy(() -> service.validate("KB-AUTO-001", "v1.1", "admin-user"))
                .isInstanceOf(AzureException.class);

        ArgumentCaptor<ConversationAuditRecord> captor = ArgumentCaptor.forClass(ConversationAuditRecord.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getQueryChatClient()).isEqualTo("admin-user");
        assertThat(captor.getValue().getTimestamp()).isNotNull();
        assertThat(captor.getValue().getResponseText()).contains("passed=false", "serviceUnavailable=true");
    }

    @Test
    void activationCanStartTheFirstActiveVersionAfterValidation() {
        when(chunkRepository.findByDocCodeAndVersion("KB-AUTO-001", "v1.1"))
                .thenReturn(List.of(chunk("v1.1", DocStatus.RETIRED)));
        when(auditRepository.findByOriginalQueryStartingWithOrderByTimestampDesc(
                        "KB_VERSION_VALIDATION|KB-AUTO-001|v1.1|"))
                .thenReturn(List.of(validationRecord(true)));
        when(chunkRepository.findByDocCodeAndStatus("KB-AUTO-001", DocStatus.ACTIVE)).thenReturn(List.of());

        KbVersionManagementService.ActivationSummary summary = service.activate("KB-AUTO-001", "v1.1", "admin");

        assertThat(summary.previousVersion()).isEmpty();
        verify(ingestionService).changeVersionStatus("KB-AUTO-001", "v1.1", DocStatus.ACTIVE);
    }

    private ChatResponse response(
            boolean escalated, boolean citationValidated, List<String> docCodes, List<SourceCitation> citations) {
        return new ChatResponse(
                UUID.randomUUID().toString(),
                "Response from candidate KB content.",
                citations,
                List.of(),
                docCodes,
                escalated,
                citationValidated,
                citationValidated ? CitationStatus.VALID : CitationStatus.NOT_APPLICABLE,
                "AnswerChatClient",
                "Question",
                "Question",
                UUID.randomUUID().toString(),
                Instant.now());
    }
}
