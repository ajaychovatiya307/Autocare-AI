package com.autocare.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autocare.ai.audit.ConversationAuditRecord;
import com.autocare.ai.audit.ConversationAuditRepository;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import com.autocare.ai.model.enums.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

class ConversationAuditAdvisorTest {

    private final ConversationAuditRepository auditRepository = Mockito.mock(ConversationAuditRepository.class);
    private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);
    private final StreamAdvisorChain streamChain = Mockito.mock(StreamAdvisorChain.class);
    private final ConversationAuditAdvisor advisor = new ConversationAuditAdvisor(auditRepository);

    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(auditRepository.countBySessionId(sessionId)).thenReturn(2L);
    }

    @Test
    void beforeOpensPendingAuditAndStampsCorrelationId() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What is the oil change interval?")))
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_SESSION_ID, sessionId,
                        ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER))
                .build();

        ChatClientRequest result = advisor.before(request, chain);

        assertThat(result.context()).containsKey(ConversationAuditAdvisor.CTX_CORRELATION_ID);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CORRELATION_ID)).isInstanceOf(UUID.class);
    }

    @Test
    void beforeRejectsMissingSessionId() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("query")))
                .context(Map.of(ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER))
                .build();

        assertThatThrownBy(() -> advisor.before(request, chain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ConversationAuditAdvisor.CTX_SESSION_ID);
    }

    @Test
    void beforeRejectsMissingActorRole() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("query")))
                .context(Map.of(ConversationAuditAdvisor.CTX_SESSION_ID, sessionId))
                .build();

        assertThatThrownBy(() -> advisor.before(request, chain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ConversationAuditAdvisor.CTX_ACTOR_ROLE);
    }

    @Test
    void afterPersistsFinalizedRecordWithAllContextFields() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What is the oil change interval?")))
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_SESSION_ID, sessionId,
                        ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER))
                .build();
        ChatClientRequest afterBefore = advisor.before(request, chain);

        SourceCitation citation = new SourceCitation("KB-AUTO-001", "v1.0", "4");
        Map<String, Object> finalizeContext = new java.util.HashMap<>(afterBefore.context());
        finalizeContext.put(ConversationAuditAdvisor.CTX_TRANSFORMED_QUERY, "What is the oil change interval for AC-SV-D?");
        finalizeContext.put(ConversationAuditAdvisor.CTX_TRANSLATION_APPLIED, false);
        finalizeContext.put(ConversationAuditAdvisor.CTX_REWRITE_APPLIED, true);
        finalizeContext.put(ConversationAuditAdvisor.CTX_EXPANSION_APPLIED, false);
        finalizeContext.put(ConversationAuditAdvisor.CTX_EXPANDED_QUERIES, List.of());
        finalizeContext.put(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of("KB-AUTO-001"));
        finalizeContext.put(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS, List.of(citation));
        finalizeContext.put(ConversationAuditAdvisor.CTX_QUERY_CHAT_CLIENT, "QueryTransformChatClient");
        finalizeContext.put(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, "AnswerChatClient");
        finalizeContext.put(ConversationAuditAdvisor.CTX_CITATION_STATUS, CitationStatus.VALID);
        finalizeContext.put(ConversationAuditAdvisor.CTX_ESCALATED, false);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(
                new AssistantMessage("Every 10,000 km or 12 months, whichever comes first."),
                ChatGenerationMetadata.NULL)));
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(finalizeContext)
                .build();

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result).isSameAs(response);
        ArgumentCaptor<ConversationAuditRecord> captor = ArgumentCaptor.forClass(ConversationAuditRecord.class);
        verify(auditRepository).save(captor.capture());
        ConversationAuditRecord saved = captor.getValue();

        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getTurnSeq()).isEqualTo(3);
        assertThat(saved.getActorRole()).isEqualTo(UserRole.OWNER);
        assertThat(saved.getOriginalQuery()).isEqualTo("What is the oil change interval?");
        assertThat(saved.getRedactedQuery()).isEqualTo("What is the oil change interval?");
        assertThat(saved.getTransformedQuery()).isEqualTo("What is the oil change interval for AC-SV-D?");
        assertThat(saved.isRewriteApplied()).isTrue();
        assertThat(saved.isTranslationApplied()).isFalse();
        assertThat(saved.getRetrievedDocCodes()).containsExactly("KB-AUTO-001");
        assertThat(saved.getRetrievedChunkRefs()).containsExactly(citation);
        assertThat(saved.getAnswerChatClient()).isEqualTo("AnswerChatClient");
        assertThat(saved.getResponseText()).isEqualTo("Every 10,000 km or 12 months, whichever comes first.");
        assertThat(saved.getCitationStatus()).isEqualTo(CitationStatus.VALID);
        assertThat(saved.isEscalationStatus()).isFalse();
        assertThat(saved.getCorrelationId()).isEqualTo(afterBefore.context().get(ConversationAuditAdvisor.CTX_CORRELATION_ID));
    }

    @Test
    void afterUsesResponseTextOverrideWhenNoModelResponsePresent() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("car vibrating")))
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_SESSION_ID, sessionId,
                        ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER))
                .build();
        ChatClientRequest afterBefore = advisor.before(request, chain);

        Map<String, Object> finalizeContext = new java.util.HashMap<>(afterBefore.context());
        finalizeContext.put(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, "AnswerChatClient");
        finalizeContext.put(ConversationAuditAdvisor.CTX_ESCALATED, true);
        finalizeContext.put(ConversationAuditAdvisor.CTX_RESPONSE_TEXT_OVERRIDE,
                "No matching vehicle service or warranty information was found in the AutoCare Knowledge Base. "
                        + "Please contact an authorized service advisor.");

        ChatClientResponse response = ChatClientResponse.builder()
                .context(finalizeContext)
                .build();

        advisor.after(response, chain);

        ArgumentCaptor<ConversationAuditRecord> captor = ArgumentCaptor.forClass(ConversationAuditRecord.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseText()).contains("No matching vehicle service");
        assertThat(captor.getValue().isEscalationStatus()).isTrue();
        assertThat(captor.getValue().getCitationStatus()).isEqualTo(CitationStatus.NOT_APPLICABLE);
    }

    @Test
    void streamingAuditPersistsOneCompleteAnswerAfterAllChunks() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What service is due?")))
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_SESSION_ID, sessionId,
                        ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER,
                        ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, "AnswerChatClient"))
                .build();
        when(streamChain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest auditedRequest = invocation.getArgument(0);
            Map<String, Object> context = new java.util.HashMap<>(auditedRequest.context());
            context.put(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT, "AnswerChatClient");
            return Flux.just(
                    streamingResponse("Standard Service ", context),
                    streamingResponse("B is due now.", context));
        });

        List<ChatClientResponse> responses = advisor.adviseStream(request, streamChain).collectList().block();

        assertThat(responses).hasSize(2);
        ArgumentCaptor<ConversationAuditRecord> captor = ArgumentCaptor.forClass(ConversationAuditRecord.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseText()).isEqualTo("Standard Service B is due now.");
    }

    @Test
    void afterThrowsWhenAnswerChatClientMissing() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("query")))
                .context(Map.of(
                        ConversationAuditAdvisor.CTX_SESSION_ID, sessionId,
                        ConversationAuditAdvisor.CTX_ACTOR_ROLE, UserRole.OWNER))
                .build();
        ChatClientRequest afterBefore = advisor.before(request, chain);

        ChatClientResponse response = ChatClientResponse.builder()
                .context(afterBefore.context())
                .build();

        assertThatThrownBy(() -> advisor.after(response, chain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ConversationAuditAdvisor.CTX_ANSWER_CHAT_CLIENT);

        verify(auditRepository, Mockito.never()).save(any());
    }

    @Test
    void afterIsNoOpWhenBeforeNeverRan() {
        ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result).isSameAs(response);
        verify(auditRepository, Mockito.never()).save(any());
    }

    @Test
    void runsImmediatelyAfterRedactionAdvisor() {
        assertThat(advisor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100);
    }

    private ChatClientResponse streamingResponse(String text, Map<String, Object> context) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(
                new AssistantMessage(text), ChatGenerationMetadata.NULL)));
        return ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();
    }
}
