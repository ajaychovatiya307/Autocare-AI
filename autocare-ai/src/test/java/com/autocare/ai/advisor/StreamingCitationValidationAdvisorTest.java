package com.autocare.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.enums.CitationStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class StreamingCitationValidationAdvisorTest {

    private final StreamingCitationValidationAdvisor advisor = new StreamingCitationValidationAdvisor();
    private final StreamAdvisorChain chain = Mockito.mock(StreamAdvisorChain.class);
    private final ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(new UserMessage("What is the oil change interval?")))
            .context(Map.of())
            .build();

    private static Document retrievedDocument(String docCode) {
        return Document.builder()
                .text("content")
                .metadata(Map.of("doc_code", docCode, "version", "v1.0", "section", "2"))
                .build();
    }

    @Test
    void passesThroughAllChunksUnchangedWhenGrounded() {
        Map<String, Object> retrievalContext =
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of(retrievedDocument("KB-AUTO-001")));
        when(chain.nextStream(request)).thenReturn(Flux.just(
                chunkResponse("Change the ", retrievalContext),
                chunkResponse("oil every 10,000 km.", retrievalContext)));

        StepVerifier.create(advisor.adviseStream(request, chain))
                .assertNext(chunk -> assertThat(chunk.chatResponse().getResult().getOutput().getText())
                        .isEqualTo("Change the "))
                .assertNext(chunk -> {
                    assertThat(chunk.chatResponse().getResult().getOutput().getText())
                            .isEqualTo("oil every 10,000 km.");
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                            .isEqualTo(CitationStatus.VALID);
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES))
                            .isEqualTo(List.of("KB-AUTO-001"));
                })
                .verifyComplete();
    }

    @Test
    void collapsesStreamToSingleBlockedChunkWhenNoEvidenceRetrieved() {
        when(chain.nextStream(request)).thenReturn(Flux.just(
                chunkResponse("Change the ", Map.of()),
                chunkResponse("oil every 10,000 km.", Map.of())));

        StepVerifier.create(advisor.adviseStream(request, chain))
                .assertNext(chunk -> {
                    assertThat(chunk.chatResponse().getResult().getOutput().getText())
                            .contains("could not verify this answer");
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                            .isEqualTo(CitationStatus.BLOCKED);
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
                })
                .verifyComplete();
    }

    @Test
    void marksNotApplicableForFixedEmptyContextEscalation() {
        when(chain.nextStream(request)).thenReturn(
                Flux.just(chunkResponse(NoMatchingContextException.ESCALATION_MESSAGE, Map.of())));

        StepVerifier.create(advisor.adviseStream(request, chain))
                .assertNext(chunk -> {
                    assertThat(chunk.chatResponse().getResult().getOutput().getText())
                            .isEqualTo(NoMatchingContextException.ESCALATION_MESSAGE);
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                            .isEqualTo(CitationStatus.NOT_APPLICABLE);
                })
                .verifyComplete();
    }

    @Test
    void replacesNoKnowledgeAnswerEvenWhenIrrelevantEvidenceWasRetrieved() {
        Map<String, Object> retrievalContext =
                Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of(retrievedDocument("KB-AUTO-001")));
        when(chain.nextStream(request)).thenReturn(Flux.just(chunkResponse("I don't know.", retrievalContext)));

        StepVerifier.create(advisor.adviseStream(request, chain))
                .assertNext(chunk -> {
                    assertThat(chunk.chatResponse().getResult().getOutput().getText())
                            .isEqualTo(NoMatchingContextException.ESCALATION_MESSAGE);
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_CITATION_STATUS))
                            .isEqualTo(CitationStatus.NOT_APPLICABLE);
                    assertThat(chunk.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
                })
                .verifyComplete();
    }

    @Test
    void emitsNothingWhenUpstreamIsEmpty() {
        when(chain.nextStream(request)).thenReturn(Flux.empty());

        StepVerifier.create(advisor.adviseStream(request, chain)).verifyComplete();
    }

    @Test
    void ordersSameAsSynchronousCitationValidation() {
        assertThat(advisor.getOrder()).isEqualTo(CitationValidationAdvisor.ORDER);
    }

    private ChatClientResponse chunkResponse(String text, Map<String, Object> context) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text), ChatGenerationMetadata.NULL)));
        return ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();
    }
}
