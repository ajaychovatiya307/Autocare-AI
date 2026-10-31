package com.autocare.ai.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientConfigTest {

    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    private final ChatClient.Builder queryTransformBuilder = mock(ChatClient.Builder.class);
    private final ChatClient.Builder answerBuilder = mock(ChatClient.Builder.class);
    private final ChatClient queryTransformChatClient = mock(ChatClient.class);
    private final ChatClient answerChatClient = mock(ChatClient.class);

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withBean(ChatClient.Builder.class, () -> chatClientBuilder)
                    .withPropertyValues(
                            "autocare.chat.query-transform-deployment=query-transform-model",
                            "autocare.chat.answer-deployment=answer-model");

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.clone()).thenReturn(queryTransformBuilder, answerBuilder);
        when(queryTransformBuilder.defaultOptions(any(ChatOptions.class))).thenReturn(queryTransformBuilder);
        when(answerBuilder.defaultOptions(any(ChatOptions.class))).thenReturn(answerBuilder);
        when(queryTransformBuilder.build()).thenReturn(queryTransformChatClient);
        when(answerBuilder.build()).thenReturn(answerChatClient);
    }

    @Test
    void registersChatClientsWithTheirConfiguredAzureDeployments() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("queryTransformChatClient");
            assertThat(context).hasBean("answerChatClient");
            assertThat(context.getBean("queryTransformChatClient")).isSameAs(queryTransformChatClient);
            assertThat(context.getBean("answerChatClient")).isSameAs(answerChatClient);

            ArgumentCaptor<ChatOptions> queryTransformOptions = ArgumentCaptor.forClass(ChatOptions.class);
            verify(queryTransformBuilder).defaultOptions(queryTransformOptions.capture());
            assertThat(queryTransformOptions.getValue())
                    .isInstanceOfSatisfying(AzureOpenAiChatOptions.class,
                            options -> {
                                assertThat(options.getDeploymentName()).isEqualTo("query-transform-model");
                                assertThat(options.getTemperature()).isEqualTo(1.0);
                            });

            ArgumentCaptor<ChatOptions> answerOptions = ArgumentCaptor.forClass(ChatOptions.class);
            verify(answerBuilder).defaultOptions(answerOptions.capture());
            assertThat(answerOptions.getValue())
                    .isInstanceOfSatisfying(AzureOpenAiChatOptions.class,
                            options -> {
                                assertThat(options.getDeploymentName()).isEqualTo("answer-model");
                                assertThat(options.getTemperature()).isEqualTo(1.0);
                            });
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AutoCareProperties.class)
    @Import(ChatClientConfig.class)
    static class TestConfig {
    }
}
