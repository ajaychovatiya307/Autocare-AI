package com.autocare.ai.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import com.autocare.ai.config.PromptTemplatesConfig;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiQueryExpanderConfigTest {

    private final ChatClient queryTransformChatClient = mock(ChatClient.class);
    private final ChatClient.Builder queryTransformChatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient multiQueryChatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withBean("queryTransformChatClient", ChatClient.class, () -> queryTransformChatClient);

    @BeforeEach
    void setUp() {
        clearInvocations(
                queryTransformChatClient,
                queryTransformChatClientBuilder,
                multiQueryChatClient,
                requestSpec,
                userSpec,
                responseSpec);
        when(queryTransformChatClient.mutate()).thenReturn(queryTransformChatClientBuilder);
        when(queryTransformChatClientBuilder.build()).thenReturn(multiQueryChatClient);
        when(multiQueryChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyConsumer())).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> userPrompt = invocation.getArgument(0);
            userPrompt.accept(userSpec);
            return requestSpec;
        });
        when(userSpec.text(any(String.class))).thenReturn(userSpec);
        when(userSpec.param(any(String.class), any())).thenReturn(userSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(
                "car vibration symptoms and service guidance\n"
                        + "vehicle vibration inspection guidance\n"
                        + "AutoCare guidance for a vibrating car");
    }

    @Test
    void expandsBroadQueryAndIncludesOriginalWithThreeVariants() {
        contextRunner.run(context -> {
            Query input = Query.builder()
                    .text("car vibrating")
                    .history(new UserMessage("The vehicle is an AC-EV-1."))
                    .context(Map.of("correlationId", "test-correlation"))
                    .build();

            var expanded = context.getBean(QueryExpander.class).expand(input);

            assertThat(expanded).hasSize(4);
            assertThat(expanded).extracting(Query::text).containsExactly(
                    "car vibrating",
                    "car vibration symptoms and service guidance",
                    "vehicle vibration inspection guidance",
                    "AutoCare guidance for a vibrating car");
            assertThat(expanded.get(1).history()).isSameAs(input.history());
            assertThat(expanded.get(1).context()).containsEntry("correlationId", "test-correlation");
            assertThat(expanded.get(0).context())
                    .containsEntry(
                            com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_EXPANSION_APPLIED, true)
                    .containsEntry(
                            com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_QUERY_CHAT_CLIENT,
                            "QueryTransformChatClient")
                    .containsKey(com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_EXPANDED_QUERIES);
            verify(multiQueryChatClient).prompt();
            verify(userSpec).param("number", 3);

            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(userSpec).text(prompt.capture());
            assertThat(prompt.getValue())
                    .contains("must not add symptoms, vehicle facts, component")
                    .contains("must not add")
                    .contains("warranty/entitlement assumptions");
        });
    }

    @Test
    void leavesSpecificStandaloneQueryUnchangedWithoutCallingModel() {
        contextRunner.run(context -> {
            Query input = new Query("What is the oil change interval for an AC-EV-1?");

            assertThat(context.getBean(QueryExpander.class).expand(input)).containsExactly(input);
            verify(multiQueryChatClient, never()).prompt();
        });
    }

    @Test
    void leavesDetailedQueriesAboutBroadTermsUnchanged() {
        contextRunner.run(context -> {
            Query input = new Query("What is the vehicle service schedule for an AC-EV-1?");

            assertThat(context.getBean(QueryExpander.class).expand(input)).containsExactly(input);
            verify(multiQueryChatClient, never()).prompt();
        });
    }

    @Test
    void expandsShortGenericVehicleIssueQuery() {
        contextRunner.run(context -> {
            Query input = new Query("vehicle issue");

            assertThat(context.getBean(QueryExpander.class).expand(input)).hasSize(4);
            verify(multiQueryChatClient).prompt();
        });
    }

    @Test
    void returnsOnlyOriginalWhenModelDoesNotReturnExpectedNumberOfVariants() {
        when(responseSpec.content()).thenReturn("one alternative only");

        contextRunner.run(context -> {
            Query input = new Query("car vibrating");

            var expanded = context.getBean(QueryExpander.class).expand(input);
            assertThat(expanded).extracting(Query::text).containsExactly("car vibrating");
            assertThat(expanded.get(0).context()).containsEntry(
                    com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_QUERY_CHAT_CLIENT,
                    "QueryTransformChatClient");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MultiQueryExpanderConfig.class, PromptTemplatesConfig.class})
    static class TestConfig {
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ChatClient.PromptUserSpec> anyConsumer() {
        return (Consumer<ChatClient.PromptUserSpec>) any(Consumer.class);
    }
}
