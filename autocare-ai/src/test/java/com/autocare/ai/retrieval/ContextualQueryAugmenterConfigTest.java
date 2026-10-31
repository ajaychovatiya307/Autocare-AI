package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.exception.NoMatchingContextException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextualQueryAugmenterConfigTest {

    private AutoCareProperties properties(boolean allowEmptyContext) {
        return new AutoCareProperties(
                new AutoCareProperties.Rag(0.50, allowEmptyContext),
                new AutoCareProperties.Embedding(1536),
                new AutoCareProperties.Chat("gpt-4o-mini", "gpt-4o", 20),
                new AutoCareProperties.Kb("v1.0"),
                new AutoCareProperties.RateLimit(10, 10, 60));
    }

    @Test
    void rejectsAllowEmptyContextTrueAtStartup() {
        assertThatThrownBy(() -> new ContextualQueryAugmenterConfig(properties(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allow-empty-context");
    }

    @Test
    void throwsNoMatchingContextExceptionWhenDocumentListIsEmpty() {
        QueryAugmenter augmenter =
                new ContextualQueryAugmenterConfig(properties(false)).contextualQueryAugmenter();

        assertThatThrownBy(() -> augmenter.augment(new Query("brake pads"), List.of()))
                .isInstanceOf(NoMatchingContextException.class)
                .hasMessage(NoMatchingContextException.ESCALATION_MESSAGE);
    }

    @Test
    void throwsNoMatchingContextExceptionWhenDocumentListIsNull() {
        QueryAugmenter augmenter =
                new ContextualQueryAugmenterConfig(properties(false)).contextualQueryAugmenter();

        assertThatThrownBy(() -> augmenter.augment(new Query("brake pads"), null))
                .isInstanceOf(NoMatchingContextException.class);
    }

    @Test
    void augmentsQueryWithRetrievedContextWhenDocumentsArePresent() {
        QueryAugmenter augmenter =
                new ContextualQueryAugmenterConfig(properties(false)).contextualQueryAugmenter();

        Query result = augmenter.augment(
                new Query("What is due at 20,000 km?"),
                List.of(new Document("Standard Service B is due at 20,000 km or 24 months.")));

        assertThat(result.text())
                .contains("What is due at 20,000 km?")
                .contains("Standard Service B is due at 20,000 km or 24 months.");
    }
}
