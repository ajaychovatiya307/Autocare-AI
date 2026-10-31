package com.autocare.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.dto.SourceCitation;
import com.autocare.ai.model.enums.CitationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.prompt.Prompt;

class CitationValidationAdvisorTest {

    private final CitationValidationAdvisor advisor = new CitationValidationAdvisor();
    private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);

    private static Document retrievedDocument(String docCode) {
        return Document.builder()
                .text("content")
                .metadata(Map.of("doc_code", docCode, "version", "v1.0", "section", "2"))
                .build();
    }

    @Test
    void marksValidWhenDocumentsWereRetrieved() {
        ChatClientResponse response = responseWithAnswer(
                "Change the oil every 10,000 km.",
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of(retrievedDocument("KB-AUTO-001"))));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo("Change the oil every 10,000 km.");
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                .isEqualTo(CitationStatus.VALID);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES))
                .isEqualTo(List.of("KB-AUTO-001"));
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_RETRIEVED_CHUNK_REFS))
                .isEqualTo(List.of(new SourceCitation("KB-AUTO-001", "v1.0", "2")));
    }

    @Test
    void blocksUnsupportedClaimWhenNoDocumentsRetrieved() {
        ChatClientResponse response = responseWithAnswer("Change the oil every 10,000 km.", Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("could not verify this answer");
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                .isEqualTo(CitationStatus.BLOCKED);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
    }

    @Test
    void blocksUnsupportedClaimWhenDocumentListIsEmpty() {
        ChatClientResponse response = responseWithAnswer(
                "Change the oil every 10,000 km.",
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of()));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                .isEqualTo(CitationStatus.BLOCKED);
    }

    @Test
    void marksNotApplicableForFixedEmptyContextEscalation() {
        ChatClientResponse response = responseWithAnswer(NoMatchingContextException.ESCALATION_MESSAGE, Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo(NoMatchingContextException.ESCALATION_MESSAGE);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                .isEqualTo(CitationStatus.NOT_APPLICABLE);
    }

    @Test
    void returnsNoMatchingContextWhenModelCannotAnswerDespiteIrrelevantRetrievedDocuments() {
        ChatClientResponse response = responseWithAnswer(
                "I don't know.",
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of(retrievedDocument("KB-AUTO-001"))));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo(NoMatchingContextException.ESCALATION_MESSAGE);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                .isEqualTo(CitationStatus.NOT_APPLICABLE);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES)).isEqualTo(List.of());
    }

    @Test
    void passesThroughWhenNoChatResponsePresent() {
        ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result).isSameAs(response);
    }

    @Test
    void ordersClosestToModelAmongDomainContentGuards() {
        assertThat(advisor.getOrder()).isEqualTo(DomainSafeguardAdvisor.ORDER + 100);
        assertThat(advisor.getOrder()).isGreaterThan(new DomainSafeguardAdvisor().getOrder());
    }

    private ChatClientResponse responseWithAnswer(String answer, Map<String, Object> context) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(answer), ChatGenerationMetadata.NULL)));
        return ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();
    }
}
