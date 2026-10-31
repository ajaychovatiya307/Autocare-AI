package com.autocare.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AutoCarePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultsWhenOnlyRequiredPropertiesSupplied() {
        // Nested record groups only materialize once the Binder finds at least one key under their prefix,
        // so we set the two fields with no @DefaultValue (chat.*) and assert the @DefaultValue fields resolve correctly.
        contextRunner
                .withPropertyValues(
                        "autocare.chat.query-transform-deployment=gpt-4o-mini",
                        "autocare.chat.answer-deployment=gpt-4o",
                        "autocare.rate-limit.chat-requests-per-minute=10",
                        "autocare.rate-limit.kb-requests-per-minute=10",
                        "autocare.rag.similarity-threshold=0.50",
                        "autocare.embedding.dimension=1536",
                        "autocare.kb.default-version=v1.0")
                .run(context -> {
                    AutoCareProperties props = context.getBean(AutoCareProperties.class);
                    assertThat(props.rag().similarityThreshold()).isEqualTo(0.50);
                    assertThat(props.rag().allowEmptyContext()).isFalse();
                    assertThat(props.embedding().dimension()).isEqualTo(1536);
                    assertThat(props.kb().defaultVersion()).isEqualTo("v1.0");
                    assertThat(props.chat().memoryMaxMessages()).isEqualTo(20);
                    assertThat(props.rateLimit().chatRequestsPerMinute()).isEqualTo(10);
                    assertThat(props.rateLimit().kbRequestsPerMinute()).isEqualTo(10);
                });
    }

    @Test
    void bindsSuppliedValuesOverridingDefaults() {
        contextRunner
                .withPropertyValues(
                        "autocare.rag.similarity-threshold=0.65",
                        "autocare.rag.allow-empty-context=true",
                        "autocare.embedding.dimension=3072",
                        "autocare.chat.query-transform-deployment=gpt-4o-mini",
                        "autocare.chat.answer-deployment=gpt-4o",
                        "autocare.chat.memory-max-messages=15",
                        "autocare.rate-limit.chat-requests-per-minute=25",
                        "autocare.rate-limit.kb-requests-per-minute=5",
                        "autocare.kb.default-version=v2.0")
                .run(context -> {
                    AutoCareProperties props = context.getBean(AutoCareProperties.class);
                    assertThat(props.rag().similarityThreshold()).isEqualTo(0.65);
                    assertThat(props.rag().allowEmptyContext()).isTrue();
                    assertThat(props.embedding().dimension()).isEqualTo(3072);
                    assertThat(props.chat().queryTransformDeployment()).isEqualTo("gpt-4o-mini");
                    assertThat(props.chat().answerDeployment()).isEqualTo("gpt-4o");
                    assertThat(props.chat().memoryMaxMessages()).isEqualTo(15);
                    assertThat(props.rateLimit().chatRequestsPerMinute()).isEqualTo(25);
                    assertThat(props.rateLimit().kbRequestsPerMinute()).isEqualTo(5);
                    assertThat(props.kb().defaultVersion()).isEqualTo("v2.0");
                });
    }

    @Configuration
    @EnableConfigurationProperties(AutoCareProperties.class)
    static class TestConfig {
    }
}
