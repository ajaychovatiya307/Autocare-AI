package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.exception.NoMatchingContextException;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Wraps Spring AI's {@link ContextualQueryAugmenter} so that an empty retrieved-context list raises
 * {@link NoMatchingContextException} — a controlled escalation (architecture.md §4.1/§4.2, FR-014) —
 * instead of Spring AI's default behavior of rendering a generic "no context" prompt to the LLM.
 *
 * <p>{@code autocare.rag.allow-empty-context} must be {@code false}; this is asserted at startup
 * rather than silently accepted, since a value of {@code true} would defeat the escalation contract.
 */
@Configuration(proxyBeanMethods = false)
public class ContextualQueryAugmenterConfig {

    private final AutoCareProperties properties;

    public ContextualQueryAugmenterConfig(AutoCareProperties properties) {
        Assert.isTrue(!properties.rag().allowEmptyContext(), "autocare.rag.allow-empty-context must be false (FR-014)");
        this.properties = properties;
    }

    @Bean
    public QueryAugmenter contextualQueryAugmenter() {
        ContextualQueryAugmenter delegate = ContextualQueryAugmenter.builder()
                .allowEmptyContext(properties.rag().allowEmptyContext())
                .build();

        return (query, documents) -> {
            if (documents == null || documents.isEmpty()) {
                throw new NoMatchingContextException();
            }
            return delegate.augment(query, documents);
        };
    }
}
