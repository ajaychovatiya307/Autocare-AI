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
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.MetadataFilterBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private ChatClient answerChatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private ChatClient.StreamResponseSpec streamSpec;
    private ChatClient.AdvisorSpec advisorSpec;

    private MetadataFilterBuilder metadataFilterBuilder;
    private ConversationAuditRepository auditRepository;
    private RetrievalAugmentationConfig retrievalAugmentationConfig;
    private CitationValidationAdvisor citationValidationAdvisor;
    private StreamingCitationValidationAdvisor streamingCitationValidationAdvisor;
    private PiiVehicleRedactionAdvisor redactionAdvisor;
    private ConversationAuditAdvisor auditAdvisor;
    private SimpleLoggerAdvisor loggerAdvisor;
    private DomainSafeguardAdvisor domainSafeguardAdvisor;
    private RetrievalAugmentationAdvisor ragAdvisor;
    private MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private ChatMemory chatMemory;

    private ChatService chatService;

    private static final MetadataFilterBuilder.Filter FILTER =
            new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, null, UserRole.ADVISOR, "AC-SD-P");

    private final ChatRequest request = new ChatRequest(
            "b6b6b6c2-8a2b-4e9b-9a3d-6b8c2f6e1a10",
            UserRole.ADVISOR,
            "AC-SD-P",
            "What is due at 20,000 km?",
            "en");

    @BeforeEach
    void setUp() {
        answerChatClient = Mockito.mock(ChatClient.class);
        requestSpec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = Mockito.mock(ChatClient.CallResponseSpec.class);
        streamSpec = Mockito.mock(ChatClient.StreamResponseSpec.class);
        advisorSpec = Mockito.mock(ChatClient.AdvisorSpec.class, Mockito.RETURNS_SELF);

        metadataFilterBuilder = Mockito.mock(MetadataFilterBuilder.class);
        auditRepository = Mockito.mock(ConversationAuditRepository.class);
        retrievalAugmentationConfig = Mockito.mock(RetrievalAugmentationConfig.class);
        ragAdvisor = Mockito.mock(RetrievalAugmentationAdvisor.class);
        citationValidationAdvisor = new CitationValidationAdvisor();
        streamingCitationValidationAdvisor = new StreamingCitationValidationAdvisor();
        redactionAdvisor = new PiiVehicleRedactionAdvisor();
        auditAdvisor = Mockito.mock(ConversationAuditAdvisor.class);
        loggerAdvisor = SimpleLoggerAdvisor.builder().build();
        domainSafeguardAdvisor = new DomainSafeguardAdvisor();
        messageChatMemoryAdvisor = Mockito.mock(MessageChatMemoryAdvisor.class);
        chatMemory = Mockito.mock(ChatMemory.class);

        when(answerChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(auditRepository.findBySessionIdOrderByTurnSeqAsc(any(UUID.class))).thenReturn(List.of());
        when(requestSpec.advisors(any(Consumer.class))).thenAnswer(invocation -> {
            invocation.<Consumer<ChatClient.AdvisorSpec>>getArgument(0).accept(advisorSpec);
            return requestSpec;
        });
        when(requestSpec.call()).thenReturn(callSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);

        when(metadataFilterBuilder.build(UserRole.ADVISOR, "AC-SD-P", DocStatus.ACTIVE, null)).thenReturn(FILTER);
        when(retrievalAugmentationConfig.advisorFor(FILTER)).thenReturn(ragAdvisor);

        chatService = new ChatService(
                answerChatClient,
                auditRepository,
                metadataFilterBuilder,
                retrievalAugmentationConfig,
                redactionAdvisor,
                auditAdvisor,
                loggerAdvisor,
                citationValidationAdvisor,
                streamingCitationValidationAdvisor,
                domainSafeguardAdvisor,
                messageChatMemoryAdvisor,
                chatMemory,
                new PromptTemplate("Authenticated role: {role}; answer in {language}."));
    }

    private ChatClientResponse groundedResponse() {
        return ChatClientResponse.builder()
                .chatResponse(org.springframework.ai.chat.model.ChatResponse.builder()
                        .generations(List.of(new Generation(
                                new AssistantMessage("Standard Service B is due at 20,000 km."))))
                        .build())
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.VALID,
                        ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of("KB-AUTO-002"),
                        ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS,
                                List.of(new SourceCitation("KB-AUTO-002", "v1.0", "2")),
                        ConversationAuditAdvisor.CTX_TRANSFORMED_QUERY, "service due at 20000 km",
                        ConversationAuditAdvisor.CTX_REWRITE_APPLIED, true))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Advisor> capturedAdvisors() {
        ArgumentCaptor<List<Advisor>> captor = ArgumentCaptor.forClass(List.class);
        verify(advisorSpec).advisors(captor.capture());
        return captor.getValue();
    }

    @Test
    void groundedAnswerIsReturnedWithCitationsAndNoEscalation() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        com.autocare.ai.model.dto.ChatResponse response = chatService.chat(request);

        assertThat(response.answer()).isEqualTo("Standard Service B is due at 20,000 km.");
        assertThat(response.citations()).containsExactly(new SourceCitation("KB-AUTO-002", "v1.0", "2"));
        assertThat(response.retrievedDocCodes()).containsExactly("KB-AUTO-002");
        assertThat(response.citationStatus()).isEqualTo(CitationStatus.VALID);
        assertThat(response.citationValidated()).isTrue();
        assertThat(response.escalated()).isFalse();
        assertThat(response.chatClient()).isEqualTo(ChatService.ANSWER_CHAT_CLIENT);
        assertThat(response.originalQuery()).isEqualTo("What is due at 20,000 km?");
        assertThat(response.transformedQuery()).isEqualTo("service due at 20000 km");
        assertThat(response.transformationsApplied()).containsExactly("REWRITE");
        assertThat(response.sessionId()).isEqualTo(request.sessionId());
        assertThat(response.correlationId()).isNotBlank();
    }

    @Test
    void usesSpringChatMemoryBySessionIdInsteadOfReadingAllAuditTurns() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        chatService.chat(new ChatRequest(
                request.sessionId(), request.role(), request.vehicleFamily(), "What about its warranty?", "en"));

        verify(auditRepository, Mockito.never()).findBySessionIdOrderByTurnSeqAsc(any(UUID.class));
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, request.sessionId());
    }

    @Test
    void redactsSensitiveValuesFromOriginalAndFallbackTransformedQueryInResponse() {
        String rawQuery = "VIN 1HGCM82633A004352, address: 221B Baker Street, London.";
        when(callSpec.chatClientResponse()).thenReturn(ChatClientResponse.builder()
                .chatResponse(org.springframework.ai.chat.model.ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage("Grounded response."))))
                        .build())
                .context(Map.of(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.VALID))
                .build());

        com.autocare.ai.model.dto.ChatResponse response = chatService.chat(new ChatRequest(
                request.sessionId(), request.role(), request.vehicleFamily(), rawQuery, "en"));

        assertThat(response.originalQuery())
                .contains("[VIN-REDACTED]", "[ADDRESS-REDACTED]")
                .doesNotContain("1HGCM82633A004352", "221B Baker Street", "London");
        assertThat(response.transformedQuery()).isEqualTo(response.originalQuery());
    }

    @Test
    void emptyContextPropagatesToTheErrorResponseHandlerAndKeepsTheAuditRecord() {
        when(callSpec.chatClientResponse()).thenThrow(new NoMatchingContextException());

        assertThatThrownBy(() -> chatService.chat(request)).isInstanceOf(NoMatchingContextException.class);
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void malformedVehicleFamilyReturnsClarificationWithoutCallingTheModel() {
        when(metadataFilterBuilder.needsVehicleFamilyClarification("AC-EV1")).thenReturn(true);

        com.autocare.ai.model.dto.ChatResponse response = chatService.chat(new ChatRequest(
                request.sessionId(), request.role(), "AC-EV1", request.query(), "en"));

        assertThat(response.answer()).contains("unambiguous supported vehicle-family code");
        assertThat(response.citations()).isEmpty();
        assertThat(response.escalated()).isFalse();
        assertThat(response.citationStatus()).isEqualTo(CitationStatus.NOT_APPLICABLE);
        verify(answerChatClient, Mockito.never()).prompt();
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void streamingMalformedVehicleFamilyReturnsClarificationWithoutCallingTheModel() {
        when(metadataFilterBuilder.needsVehicleFamilyClarification("AC-EV1")).thenReturn(true);

        List<com.autocare.ai.model.dto.ChatResponse> responses = chatService.streamChat(new ChatRequest(
                        request.sessionId(), request.role(), "AC-EV1", request.query(), "en"))
                .collectList()
                .block();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.answer()).contains("unambiguous supported vehicle-family code");
            assertThat(response.escalated()).isFalse();
            assertThat(response.citationStatus()).isEqualTo(CitationStatus.NOT_APPLICABLE);
        });
        verify(answerChatClient, Mockito.never()).prompt();
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void ownerCannotRetrieveAdminOnlyKnowledgeBaseIngestionDetails() {
        when(callSpec.chatClientResponse()).thenThrow(new NoMatchingContextException());
        assertThatThrownBy(() -> chatService.chat(new ChatRequest(
                        request.sessionId(),
                        UserRole.OWNER,
                        "all",
                        "(Owner) How do I add a new KB document?",
                        "en")))
                .isInstanceOf(NoMatchingContextException.class);
        verify(metadataFilterBuilder).build(UserRole.OWNER, "all", DocStatus.ACTIVE, null);
        verify(answerChatClient).prompt();
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void ownerCannotRetrieveAdvisorOnlyInternalSlaTargets() {
        when(callSpec.chatClientResponse()).thenThrow(new NoMatchingContextException());
        assertThatThrownBy(() -> chatService.chat(new ChatRequest(
                        request.sessionId(),
                        UserRole.OWNER,
                        "all",
                        "An owner asks for the internal SLA targets.",
                        "en")))
                .isInstanceOf(NoMatchingContextException.class);
        verify(metadataFilterBuilder).build(UserRole.OWNER, "all", DocStatus.ACTIVE, null);
    }

    @Test
    void ordinaryOwnerServiceBrakeAndWarrantyQuestionsReachTheGroundedPipeline() {
        MetadataFilterBuilder.Filter ownerFilter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, null, UserRole.OWNER, "all");
        when(metadataFilterBuilder.build(UserRole.OWNER, "all", DocStatus.ACTIVE, null)).thenReturn(ownerFilter);
        when(retrievalAugmentationConfig.advisorFor(ownerFilter)).thenReturn(ragAdvisor);
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        List<String> ownerQuestions = List.of(
                "What service does my vehicle need?",
                "My brakes are making a grinding noise; what should I do?",
                "What does my warranty cover?");
        for (String query : ownerQuestions) {
            com.autocare.ai.model.dto.ChatResponse response = chatService.chat(new ChatRequest(
                    UUID.randomUUID().toString(), UserRole.OWNER, "all", query, "en"));

            assertThat(response.escalated()).isFalse();
            assertThat(response.citationStatus()).isEqualTo(CitationStatus.VALID);
            assertThat(response.citations()).isNotEmpty();
        }

        verify(answerChatClient, Mockito.times(ownerQuestions.size())).prompt();
        verify(metadataFilterBuilder, Mockito.times(ownerQuestions.size()))
                .build(UserRole.OWNER, "all", DocStatus.ACTIVE, null);
    }

    @Test
    void syncPipelineRegistersEveryStageExactlyOnceInArchitectureOrder() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        chatService.chat(request);

        List<Advisor> advisors = capturedAdvisors();
        assertThat(advisors)
                .containsExactly(
                        redactionAdvisor,
                        messageChatMemoryAdvisor,
                        auditAdvisor,
                        loggerAdvisor,
                        ragAdvisor,
                        citationValidationAdvisor,
                        domainSafeguardAdvisor)
                .doesNotContain(streamingCitationValidationAdvisor);
    }

    @Test
    void syncPipelineSuppliesRequiredAuditContextParameters() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        chatService.chat(request);

        verify(advisorSpec).param(ConversationAuditAdvisor.CTX_SESSION_ID, request.sessionId());
        verify(advisorSpec).param(ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.ADVISOR);
        verify(advisorSpec)
                .param(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, ChatService.ANSWER_CHAT_CLIENT);
        verify(advisorSpec).param(Mockito.eq(ConversationAuditAdvisor.CTX_CORRELATION_ID), any());
    }

    @Test
    void suppliesRoleSpecificGroundingAndNoActionInstructionsToTheAnswerModel() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        chatService.chat(request);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        assertThat(systemPrompt.getValue())
                .contains("Authenticated role: ADVISOR")
                .contains("answer in English");
    }

    @Test
    void streamPipelineSubstitutesTheStreamingCitationValidationAdvisor() {
        when(streamSpec.chatClientResponse()).thenReturn(Flux.just(groundedResponse()));

        List<com.autocare.ai.model.dto.ChatResponse> responses =
                chatService.streamChat(request).collectList().block();

        assertThat(responses).singleElement().satisfies(response ->
                assertThat(response.answer()).isEqualTo("Standard Service B is due at 20,000 km."));

        List<Advisor> advisors = capturedAdvisors();
        assertThat(advisors)
                .containsExactly(
                        redactionAdvisor,
                        messageChatMemoryAdvisor,
                        auditAdvisor,
                        loggerAdvisor,
                        ragAdvisor,
                        streamingCitationValidationAdvisor,
                        domainSafeguardAdvisor);
        assertThat(responses.get(0).mode()).isEqualTo("ASYNC");
        assertThat(responses.get(0).advisorsUsed())
                .contains("RetrievalAugmentationAdvisor", "MessageChatMemoryAdvisor");
    }

    @Test
    void ownerStreamingPreservesEachValidatedAnswerChunk() {
        MetadataFilterBuilder.Filter ownerFilter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, null, UserRole.OWNER, "all");
        when(metadataFilterBuilder.build(UserRole.OWNER, "all", DocStatus.ACTIVE, null)).thenReturn(ownerFilter);
        when(retrievalAugmentationConfig.advisorFor(ownerFilter)).thenReturn(ragAdvisor);
        when(streamSpec.chatClientResponse()).thenReturn(Flux.just(
                responseWithText("Standard Service B "),
                responseWithText("is due at 20,000 km.")));
        ChatRequest ownerRequest =
                new ChatRequest(request.sessionId(), UserRole.OWNER, "all", request.query(), "en");

        List<com.autocare.ai.model.dto.ChatResponse> responses =
                chatService.streamChat(ownerRequest).collectList().block();

        assertThat(responses).extracting(com.autocare.ai.model.dto.ChatResponse::answer)
                .containsExactly("Standard Service B ", "is due at 20,000 km.");
        assertThat(responses).extracting(com.autocare.ai.model.dto.ChatResponse::mode)
                .containsOnly("ASYNC");
    }

    private ChatClientResponse responseWithText(String text) {
        org.springframework.ai.chat.model.ChatResponse chatResponse =
                org.springframework.ai.chat.model.ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(text))))
                        .build();
        return groundedResponse().mutate().chatResponse(chatResponse).build();
    }

    @Test
    void streamPipelinePropagatesNoContextForAStructuredErrorEvent() {
        when(streamSpec.chatClientResponse()).thenReturn(Flux.error(new NoMatchingContextException()));

        StepVerifier.create(chatService.streamChat(request))
                .expectError(NoMatchingContextException.class)
                .verify();
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void streamPipelineKeepsWrappedNoContextErrorsForTheControllerHandler() {
        when(streamSpec.chatClientResponse())
                .thenReturn(Flux.error(new IllegalStateException(
                        "Stream processing failed", new NoMatchingContextException())));

        StepVerifier.create(chatService.streamChat(request))
                .expectError(IllegalStateException.class)
                .verify();
        verify(auditRepository).save(any(ConversationAuditRecord.class));
    }

    @Test
    void retrievalAdvisorIsScopedToTheResolvedMetadataFilter() {
        when(callSpec.chatClientResponse()).thenReturn(groundedResponse());

        chatService.chat(request);

        verify(metadataFilterBuilder).build(UserRole.ADVISOR, "AC-SD-P", DocStatus.ACTIVE, null);
        verify(retrievalAugmentationConfig).advisorFor(FILTER);
    }
}
