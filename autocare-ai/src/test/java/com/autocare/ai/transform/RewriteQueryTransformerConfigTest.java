package com.autocare.ai.transform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import com.autocare.ai.config.PromptTemplatesConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewriteQueryTransformerConfigTest {

    private final ChatClient queryTransformChatClient = mock(ChatClient.class);
    private final ChatClient.Builder queryTransformChatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient rewriteChatClient = mock(ChatClient.class);
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
                rewriteChatClient,
                requestSpec,
                userSpec,
                responseSpec);
        when(queryTransformChatClient.mutate()).thenReturn(queryTransformChatClientBuilder);
        when(queryTransformChatClientBuilder.build()).thenReturn(rewriteChatClient);
        when(rewriteChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyConsumer())).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> userPrompt = invocation.getArgument(0);
            userPrompt.accept(userSpec);
            return requestSpec;
        });
        when(userSpec.text(any(String.class))).thenReturn(userSpec);
        when(userSpec.param(any(String.class), any())).thenReturn(userSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("What is the warranty coverage for an AC-EV-1?");
    }

    @Test
    void rewritesContextDependentFollowUpWithConversationHistory() {
        contextRunner.run(context -> {
            Query input = Query.builder()
                    .text("What about its warranty?")
                    .history(
                            new UserMessage("What is the service schedule for my AC-EV-1?"),
                            new AssistantMessage("The knowledge base has service schedule information."))
                    .context(Map.of("correlationId", "test-correlation"))
                    .build();

            Query rewritten = context.getBean(QueryTransformer.class).transform(input);

            assertThat(rewritten.text()).isEqualTo("What is the warranty coverage for an AC-EV-1?");
            assertThat(rewritten.history()).isSameAs(input.history());
            assertThat(rewritten.context()).containsEntry("correlationId", "test-correlation");
            verify(rewriteChatClient).prompt();
            verify(userSpec).param("target", "AutoCare Knowledge Base");
            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(userSpec).text(prompt.capture());
            assertThat(prompt.getValue()).contains("Do not add symptoms");
            ArgumentCaptor<Object> queryContext = ArgumentCaptor.forClass(Object.class);
            verify(userSpec).param(eq("query"), queryContext.capture());
            assertThat(queryContext.getValue()).asString()
                    .contains("What is the service schedule for my AC-EV-1?")
                    .contains("What about its warranty?");
        });
    }

    @Test
    void leavesStandaloneQueryUnchangedWithoutCallingTheModel() {
        contextRunner.run(context -> {
            Query input = Query.builder()
                    .text("What is the service schedule for an AC-EV-1?")
                    .history(new UserMessage("Hello"))
                    .build();

            assertThat(context.getBean(QueryTransformer.class).transform(input)).isSameAs(input);
            verify(rewriteChatClient, never()).prompt();
        });
    }

    @Test
    void doesNotRewriteContextReferenceWhenConversationHistoryIsUnavailable() {
        contextRunner.run(context -> {
            Query input = new Query("What about its warranty?");

            assertThat(context.getBean(QueryTransformer.class).transform(input)).isSameAs(input);
            verify(rewriteChatClient, never()).prompt();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({RewriteQueryTransformerConfig.class, PromptTemplatesConfig.class})
    static class TestConfig {
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ChatClient.PromptUserSpec> anyConsumer() {
        return (Consumer<ChatClient.PromptUserSpec>) any(Consumer.class);
    }
}
