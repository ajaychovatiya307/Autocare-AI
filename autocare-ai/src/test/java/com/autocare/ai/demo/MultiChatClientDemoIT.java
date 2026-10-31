package com.autocare.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.autocare.ai.config.AutoCareProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Proves the SRS §4.7 mandatory requirement: at least two distinct {@code ChatClient}/model pairings
 * are invoked within one session, each with explicit routing and traceable logs, rather than a single
 * client or implicit/dynamic model selection. Per testing.md §6:
 *
 * <ol>
 *   <li>Invokes {@code QueryTransformChatClient} for a query-transform-style stage of one turn.</li>
 *   <li>Invokes {@code AnswerChatClient} for the final-generation stage of the same turn.</li>
 *   <li>Asserts both invocations were logged with distinct {@code chatModel} deployment names and the
 *       same {@code sessionId}/{@code correlationId}.</li>
 * </ol>
 *
 * <p>The real {@code ChatModel} bean (Azure OpenAI) is replaced with a recording stub so the test
 * requires no live Azure OpenAI credentials; only the explicit per-{@code ChatClient} deployment
 * routing configured in {@code ChatClientConfig} (T2.1) is under test here, not the network call itself.
 */
@SpringBootTest
class MultiChatClientDemoIT {

    @TestConfiguration
    static class RecordingChatModelConfig {

        @Bean
        @Primary
        ChatModel recordingChatModel() {
            return new RecordingChatModel();
        }
    }

    /** Replaces the real Azure OpenAI {@code ChatModel} so this demo needs no live credentials. */
    static class RecordingChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("stub response"))));
        }
    }

    /**
     * Stands in for the "traceable logs" requirement of SRS §4.7: records which {@code ChatClient}
     * handled the stage, which model deployment it routed to, and the session/correlation IDs, the
     * same way production logging (e.g. {@code SimpleLoggerAdvisor}) would.
     */
    static class DemoTraceAdvisor implements BaseAdvisor {

        static final String CTX_CHAT_CLIENT = "demo.chatClient";
        static final String CTX_SESSION_ID = "demo.sessionId";
        static final String CTX_CORRELATION_ID = "demo.correlationId";

        private static final Logger LOG = LoggerFactory.getLogger(DemoTraceAdvisor.class);

        private final List<InvocationRecord> invocations;

        DemoTraceAdvisor(List<InvocationRecord> invocations) {
            this.invocations = invocations;
        }

        @Override
        public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
            Map<String, Object> context = request.context();
            String chatClient = (String) context.get(CTX_CHAT_CLIENT);
            UUID sessionId = (UUID) context.get(CTX_SESSION_ID);
            UUID correlationId = (UUID) context.get(CTX_CORRELATION_ID);
            String deployment = deploymentNameOf(request.prompt().getOptions());

            LOG.info("chatClient={} chatModel={} sessionId={} correlationId={}",
                    chatClient, deployment, sessionId, correlationId);
            invocations.add(new InvocationRecord(chatClient, deployment, sessionId, correlationId));

            return request;
        }

        @Override
        public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
            return response;
        }

        @Override
        public String getName() {
            return DemoTraceAdvisor.class.getSimpleName();
        }

        @Override
        public int getOrder() {
            return 0;
        }

        private String deploymentNameOf(ChatOptions options) {
            return options instanceof AzureOpenAiChatOptions azureOptions ? azureOptions.getDeploymentName() : null;
        }
    }

    record InvocationRecord(String chatClient, String chatModelDeployment, UUID sessionId, UUID correlationId) {
    }

    @Autowired
    @Qualifier("queryTransformChatClient")
    private ChatClient queryTransformChatClient;

    @Autowired
    @Qualifier("answerChatClient")
    private ChatClient answerChatClient;

    @Autowired
    private AutoCareProperties properties;

    @Test
    void bothChatClientsAreInvokedAndTraceableWithinOneSession() {
        UUID sessionId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        List<InvocationRecord> invocations = new CopyOnWriteArrayList<>();
        DemoTraceAdvisor tracer = new DemoTraceAdvisor(invocations);

        // Stage: a query-transform-style step (e.g. MultiQueryExpander) for a broad/ambiguous query.
        queryTransformChatClient.prompt()
                .user("car vibrating")
                .advisors(a -> a.advisors(tracer)
                        .param(DemoTraceAdvisor.CTX_CHAT_CLIENT, "QueryTransformChatClient")
                        .param(DemoTraceAdvisor.CTX_SESSION_ID, sessionId)
                        .param(DemoTraceAdvisor.CTX_CORRELATION_ID, correlationId))
                .call()
                .content();

        // Stage: the final grounded-answer generation step of the same turn.
        answerChatClient.prompt()
                .user("Standard Service B is due at 20,000 km per KB-AUTO-002.")
                .advisors(a -> a.advisors(tracer)
                        .param(DemoTraceAdvisor.CTX_CHAT_CLIENT, "AnswerChatClient")
                        .param(DemoTraceAdvisor.CTX_SESSION_ID, sessionId)
                        .param(DemoTraceAdvisor.CTX_CORRELATION_ID, correlationId))
                .call()
                .content();

        assertThat(invocations).hasSize(2);
        assertThat(invocations).extracting(InvocationRecord::chatClient)
                .containsExactlyInAnyOrder("QueryTransformChatClient", "AnswerChatClient");
        assertThat(invocations).extracting(InvocationRecord::chatModelDeployment)
                .containsExactlyInAnyOrder(
                        properties.chat().queryTransformDeployment(), properties.chat().answerDeployment());
        assertThat(invocations.stream().map(InvocationRecord::chatModelDeployment).distinct())
                .as("routing must be fixed per stage, not collapsed onto a single deployment")
                .hasSize(2);
        assertThat(invocations).allSatisfy(record -> {
            assertThat(record.sessionId()).isEqualTo(sessionId);
            assertThat(record.correlationId()).isEqualTo(correlationId);
        });
    }
}
