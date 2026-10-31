package com.autocare.ai.transform;

import com.autocare.ai.service.LanguageDetectionService;
import com.autocare.ai.config.PromptTemplatesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslationQueryTransformerConfigTest {

    private final ChatClient queryTransformChatClient = mock(ChatClient.class);
    private final ChatClient.Builder queryTransformChatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient translationChatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.PromptUserSpec userSpec = mock(ChatClient.PromptUserSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withBean("queryTransformChatClient", ChatClient.class, () -> queryTransformChatClient)
                    .withPropertyValues("autocare.rag.retrieval-language=en");

    @BeforeEach
    void setUp() {
        clearInvocations(
                queryTransformChatClient,
                queryTransformChatClientBuilder,
                translationChatClient,
                requestSpec,
                userSpec,
                responseSpec);
        when(queryTransformChatClient.mutate()).thenReturn(queryTransformChatClientBuilder);
        when(queryTransformChatClientBuilder.build()).thenReturn(translationChatClient);
        when(translationChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyConsumer())).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> userPrompt = invocation.getArgument(0);
            userPrompt.accept(userSpec);
            return requestSpec;
        });
        when(userSpec.text(any(String.class))).thenReturn(userSpec);
        when(userSpec.param(any(String.class), any())).thenReturn(userSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("What is the service interval for my car?");
    }

    @Test
    void leavesQueriesInTheRetrievalLanguageUnchangedWithoutCallingTheModel() {
        contextRunner.run(context -> {
            Query input = new Query("What is the service interval for my car?");
            QueryTransformer transformer = context.getBean(QueryTransformer.class);

            Query transformed = transformer.transform(input);
            assertThat(transformed.text()).isEqualTo(input.text());
            assertThat(transformed.context()).containsEntry(
                    com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_LANGUAGE_DETECTED, "en");
            verify(translationChatClient, never()).prompt();
        });
    }

    @Test
    void translatesWhenDetectedLanguageDiffersFromRetrievalLanguage() {
        contextRunner.run(context -> {
            QueryTransformer transformer = context.getBean(QueryTransformer.class);
            Query translated = transformer.transform(
                    new Query("¿Cuál es el intervalo de servicio para mi coche?"));

            assertThat(translated.text()).isEqualTo("What is the service interval for my car?");
            verify(translationChatClient).prompt();
            verify(userSpec).param("targetLanguage", "en");
            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(userSpec).text(prompt.capture());
            assertThat(prompt.getValue()).contains("Preserve vehicle identifiers");
        });
    }

    @Test
    void leavesUndeterminedLanguageUnchangedWithoutCallingTheModel() {
        contextRunner.run(context -> {
            Query input = new Query("AC-EV-1");
            QueryTransformer transformer = context.getBean(QueryTransformer.class);

            Query transformed = transformer.transform(input);
            assertThat(transformed.text()).isEqualTo(input.text());
            assertThat(transformed.context()).containsEntry(
                    com.autocare.ai.advisor.ConversationAuditAdvisor.CTX_LANGUAGE_DETECTED, "und");
            verify(translationChatClient, never()).prompt();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({TranslationQueryTransformerConfig.class, PromptTemplatesConfig.class})
    static class TestConfig {

        @Bean
        LanguageDetectionService languageDetectionService() {
            return new LanguageDetectionService();
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<ChatClient.PromptUserSpec> anyConsumer() {
        return (Consumer<ChatClient.PromptUserSpec>) any(Consumer.class);
    }
}
