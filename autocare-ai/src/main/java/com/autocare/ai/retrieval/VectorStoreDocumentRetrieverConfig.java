package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.enums.UserRole;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Builds the {@code VectorStoreDocumentRetriever} with the similarity threshold enforced at
 * ≥ 0.50 (SRS §4.1) and, per request, a metadata filter scoped by {@code status}/{@code version}/
 * {@code vehicle_family}/{@code audience} (architecture.md §5 items 1-4): every chunk must carry a
 * boolean {@code audience_<role>} metadata flag (e.g. {@code audience_advisor=true}) for each role
 * permitted to retrieve it, per KB-METADATA.md §4 (NFR-002, FR-024).
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreDocumentRetrieverConfig {

    private static final double MINIMUM_SIMILARITY_THRESHOLD = 0.50;

    private final VectorStore vectorStore;
    private final AutoCareProperties properties;

    public VectorStoreDocumentRetrieverConfig(VectorStore vectorStore, AutoCareProperties properties) {
        Assert.isTrue(
                properties.rag().similarityThreshold() >= MINIMUM_SIMILARITY_THRESHOLD,
                "autocare.rag.similarity-threshold must be >= 0.50 (SRS §4.1)");
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /** Default retriever (no metadata filter), e.g. for KB-wide searches. */
    @Bean
    public VectorStoreDocumentRetriever vectorStoreDocumentRetriever() {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(properties.rag().similarityThreshold())
                .build();
    }

    /** Per-request retriever scoped to the resolved {@code status}/{@code version}/{@code vehicle_family}. */
    public VectorStoreDocumentRetriever retrieverFor(MetadataFilterBuilder.Filter filter) {
        Filter.Expression expression = buildFilterExpression(filter);
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(properties.rag().similarityThreshold())
                .filterExpression(() -> expression)
                .build();
    }

    private Filter.Expression buildFilterExpression(MetadataFilterBuilder.Filter filter) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op status = b.eq("status", filter.status().name());
        FilterExpressionBuilder.Op audience = audienceFilter(b, filter.role());
        FilterExpressionBuilder.Op effectiveDate = b.lte("effective_date", LocalDate.now().toString());
        FilterExpressionBuilder.Op eligible = b.and(b.and(status, audience), effectiveDate);
        if (filter.version() != null) {
            eligible = b.and(eligible, b.eq("version", filter.version()));
        }
        if (VehicleFamilyResolver.ALL.equals(filter.vehicleFamily())) {
            return eligible.build();
        }

        FilterExpressionBuilder.Op vehicleFamily = b.or(
                b.eq("vehicle_family", filter.vehicleFamily()),
                b.eq("vehicle_family", VehicleFamilyResolver.ALL));
        return b.and(eligible, vehicleFamily).build();
    }

    private FilterExpressionBuilder.Op audienceFilter(FilterExpressionBuilder builder, UserRole role) {
        if (role != UserRole.ADMIN) {
            return builder.eq(audienceMetadataKey(role), "true");
        }
        return builder.or(
                builder.eq(audienceMetadataKey(UserRole.OWNER), "true"),
                builder.or(
                        builder.eq(audienceMetadataKey(UserRole.ADVISOR), "true"),
                        builder.eq(audienceMetadataKey(UserRole.ADMIN), "true")));
    }

    /** Metadata key a chunk must set to {@code true} to be retrievable by {@code role} (KB-METADATA.md §4). */
    static String audienceMetadataKey(UserRole role) {
        return "audience_" + role.name().toLowerCase(Locale.ROOT);
    }
}
