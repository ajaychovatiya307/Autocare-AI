package com.autocare.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds the {@code autocare.*} properties referenced throughout architecture.md
 * (similarity threshold, allowEmptyContext, embedding dimension, ChatClient deployment names, default KB version).
 */
@ConfigurationProperties(prefix = "autocare")
public record AutoCareProperties(
        Rag rag,
        Embedding embedding,
        Chat chat,
        Kb kb,
        @DefaultValue RateLimit rateLimit) {

    public record Rag(
            @DefaultValue("0.50") double similarityThreshold,
            @DefaultValue("false") boolean allowEmptyContext) {
    }

    public record Embedding(
            @DefaultValue("1536") int dimension) {
    }

    public record Chat(
            String queryTransformDeployment,
            String answerDeployment,
            @DefaultValue("20") int memoryMaxMessages) {
    }

    public record Kb(
            @DefaultValue("v1.0") String defaultVersion) {
    }

    public record RateLimit(
            @DefaultValue("10") int chatRequestsPerMinute,
            @DefaultValue("10") int kbRequestsPerMinute,
            @DefaultValue("60") int windowSeconds) {
    }
}
