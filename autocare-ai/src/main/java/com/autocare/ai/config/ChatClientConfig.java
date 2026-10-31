package com.autocare.ai.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {

    @Bean
    public ChatClient queryTransformChatClient(ChatClient.Builder chatClientBuilder, AutoCareProperties properties) {
        Assert.notNull(properties.chat(), "autocare.chat properties must be configured");
        String deployment = properties.chat().queryTransformDeployment();
        Assert.hasText(deployment, "autocare.chat.query-transform-deployment must be configured");

        return chatClientBuilder.clone()
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(deployment)
                        .temperature(1.0)
                        .build())
                .build();
    }

    @Bean
    public ChatClient answerChatClient(ChatClient.Builder chatClientBuilder, AutoCareProperties properties) {
        Assert.notNull(properties.chat(), "autocare.chat properties must be configured");
        String deployment = properties.chat().answerDeployment();
        Assert.hasText(deployment, "autocare.chat.answer-deployment must be configured");

        return chatClientBuilder.clone()
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(deployment)
                        .temperature(1.0)
                        .build())
                .build();
    }
}
