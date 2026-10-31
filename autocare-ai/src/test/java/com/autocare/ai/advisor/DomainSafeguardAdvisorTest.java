package com.autocare.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DomainSafeguardAdvisorTest {

    private final DomainSafeguardAdvisor advisor = new DomainSafeguardAdvisor();
    private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);

    @Test
    void passesThroughGroundedAnswerWithNoViolations() {
        ChatClientResponse response = responseWithAnswer(
                "Change the oil every 10,000 km or 12 months, whichever comes first.", Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo("Change the oil every 10,000 km or 12 months, whichever comes first.");
        assertThat(result.context()).doesNotContainKey(ConversationAuditAdvisor.CTX_ESCALATED);
    }

    @Test
    void blocksRepairCostClaims() {
        ChatClientResponse response = responseWithAnswer("The repair cost of $350 is expected.", Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("cannot provide repair cost estimates");
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
    }

    @Test
    void blocksCompletionDateClaims() {
        ChatClientResponse response = responseWithAnswer("Your vehicle will be ready by 15/11/2026.", Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("cannot provide repair cost estimates or completion dates");
    }

    @Test
    void blocksFinalWarrantyDecisionWording() {
        ChatClientResponse response = responseWithAnswer("Good news, your warranty claim is approved.", Map.of());

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("preliminary warranty pre-check");
    }

    @Test
    void blocksClaimsThatTheAssistantCanArrangeOrDispatchExternalServices() {
        ChatClientResponse response = responseWithAnswer(
                "I can arrange roadside assistance for you.",
                Map.of(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of("KB-AUTO-005")));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("cannot book, call, or dispatch assistance");
        assertThat(result.context().get(ConversationAuditAdvisor.CTX_ESCALATED)).isEqualTo(true);
    }

    @Test
    void blocksUnsupportedActionClaimAcrossMultipleStreamChunks() {
        StreamAdvisorChain streamChain = Mockito.mock(StreamAdvisorChain.class);
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("I need roadside assistance.")))
                .context(Map.of())
                .build();
        when(streamChain.nextStream(request)).thenReturn(Flux.just(
                streamingResponse("I can arrange ", Map.of()),
                streamingResponse("roadside assistance for you.", Map.of())));

        StepVerifier.create(advisor.adviseStream(request, streamChain))
                .assertNext(result -> assertThat(result.chatResponse().getResult().getOutput().getText())
                        .contains("cannot book, call, or dispatch assistance"))
                .verifyComplete();
    }

    @Test
    void blocksSafetyGuidanceWhenSafetyDocNotRetrieved() {
        ChatClientResponse response = responseWithAnswer(
                "Stop driving immediately and contact roadside assistance.",
                Map.of(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of("KB-AUTO-001")));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .contains("No matching safety guidance was found");
    }

    @Test
    void allowsSafetyGuidanceWhenSafetyDocRetrieved() {
        ChatClientResponse response = responseWithAnswer(
                "Stop driving immediately and contact roadside assistance.",
                Map.of(ConversationAuditAdvisor.CTX_RETRIEVED_DOC_CODES, List.of("KB-AUTO-005")));

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result.chatResponse().getResult().getOutput().getText())
                .isEqualTo("Stop driving immediately and contact roadside assistance.");
        assertThat(result.context()).doesNotContainKey(ConversationAuditAdvisor.CTX_ESCALATED);
    }

    @Test
    void passesThroughWhenNoChatResponsePresent() {
        ChatClientResponse response = ChatClientResponse.builder().context(Map.of()).build();

        ChatClientResponse result = advisor.after(response, chain);

        assertThat(result).isSameAs(response);
    }

    @Test
    void ordersAfterDomainContentGuardsAtExpectedPrecedence() {
        assertThat(advisor.getOrder()).isEqualTo(SimpleLoggerAdvisorConfig.ORDER + 200);
        assertThat(advisor.getOrder()).isGreaterThan(SimpleLoggerAdvisorConfig.ORDER);
    }

    private ChatClientResponse responseWithAnswer(String answer, Map<String, Object> context) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(answer), ChatGenerationMetadata.NULL)));
        return ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();
    }

    private ChatClientResponse streamingResponse(String answer, Map<String, Object> context) {
        return responseWithAnswer(answer, context);
    }
}
