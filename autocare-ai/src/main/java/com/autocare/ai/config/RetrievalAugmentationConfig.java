package com.autocare.ai.config;

import com.autocare.ai.retrieval.MetadataFilterBuilder;
import com.autocare.ai.retrieval.VectorStoreDocumentRetrieverConfig;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires T8.2–T8.7 into a {@link RetrievalAugmentationAdvisor} in the fixed stage order (architecture.md
 * §17 items 5-9): {@code TranslationQueryTransformer} → {@code RewriteQueryTransformer} →
 * {@code MultiQueryExpander} → {@code VectorStoreDocumentRetriever} → {@code ContextualQueryAugmenter}.
 */
@Configuration(proxyBeanMethods = false)
public class RetrievalAugmentationConfig {

    private final List<QueryTransformer> queryTransformers;
    private final QueryExpander queryExpander;
    private final VectorStoreDocumentRetrieverConfig retrieverConfig;
    private final QueryAugmenter queryAugmenter;

    public RetrievalAugmentationConfig(
            @Qualifier("translationQueryTransformer") QueryTransformer translationQueryTransformer,
            @Qualifier("rewriteQueryTransformer") QueryTransformer rewriteQueryTransformer,
            QueryExpander queryExpander,
            VectorStoreDocumentRetrieverConfig retrieverConfig,
            QueryAugmenter queryAugmenter) {
        // Order must match architecture.md §17: translate, then rewrite, before expansion/retrieval.
        this.queryTransformers = List.of(translationQueryTransformer, rewriteQueryTransformer);
        this.queryExpander = queryExpander;
        this.retrieverConfig = retrieverConfig;
        this.queryAugmenter = queryAugmenter;
    }

    /** Default advisor (no role/vehicle-family scoping), e.g. for KB-wide searches. */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor() {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformers)
                .queryExpander(queryExpander)
                .documentRetriever(retrieverConfig.vectorStoreDocumentRetriever())
                .queryAugmenter(queryAugmenter)
                .build();
    }

    /** Per-request advisor scoped to the resolved {@code status}/{@code version}/{@code vehicle_family} filter. */
    public RetrievalAugmentationAdvisor advisorFor(MetadataFilterBuilder.Filter filter) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformers)
                .queryExpander(queryExpander)
                .documentRetriever(retrieverConfig.retrieverFor(filter))
                .queryAugmenter(queryAugmenter)
                .build();
    }
}
