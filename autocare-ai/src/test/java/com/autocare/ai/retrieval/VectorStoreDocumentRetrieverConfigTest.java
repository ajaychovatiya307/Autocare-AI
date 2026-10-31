package com.autocare.ai.retrieval;

import com.autocare.ai.config.AutoCareProperties;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreDocumentRetrieverConfigTest {

    private final VectorStore vectorStore = Mockito.mock(VectorStore.class);

    private AutoCareProperties propertiesWithThreshold(double threshold) {
        return new AutoCareProperties(
                new AutoCareProperties.Rag(threshold, false),
                new AutoCareProperties.Embedding(1536),
                new AutoCareProperties.Chat("gpt-4o-mini", "gpt-4o", 20),
                new AutoCareProperties.Kb("v1.0"),
                new AutoCareProperties.RateLimit(10, 10, 60));
    }

    @Test
    void rejectsConfiguredThresholdBelowMinimum() {
        assertThatThrownBy(() -> new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.50");
    }

    @Test
    void defaultRetrieverAppliesConfiguredSimilarityThreshold() {
        VectorStoreDocumentRetrieverConfig config =
                new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.65));
        when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class))).thenReturn(java.util.List.of());

        VectorStoreDocumentRetriever retriever = config.vectorStoreDocumentRetriever();
        retriever.retrieve(new Query("brake pads"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.65);
    }

    @Test
    void perRequestRetrieverAppliesStatusVersionAndVehicleFamilyFilter() {
        VectorStoreDocumentRetrieverConfig config =
                new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.50));
        when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class))).thenReturn(java.util.List.of());

        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.ADVISOR, "AC-SD-P");
        VectorStoreDocumentRetriever retriever = config.retrieverFor(filter);
        retriever.retrieve(new Query("service schedule"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.50);
        assertThat(request.hasFilterExpression()).isTrue();
        assertThat(request.getFilterExpression().toString())
                .contains("ACTIVE")
                .contains("v1.0")
                .contains("AC-SD-P")
                .contains(VehicleFamilyResolver.ALL)
                .contains("effective_date");
    }

    @Test
    void adminRetrievalIncludesAllDocumentAudienceLevels() {
        VectorStoreDocumentRetrieverConfig config =
                new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.50));
        when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class))).thenReturn(java.util.List.of());

        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.ADMIN, VehicleFamilyResolver.ALL);
        config.retrieverFor(filter).retrieve(new Query("governance"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString())
                .contains("audience_owner")
                .contains("audience_advisor")
                .contains("audience_admin");
    }

    @Test
    void allVehicleFamilyDoesNotRestrictRetrievalToGeneralChunks() {
        VectorStoreDocumentRetrieverConfig config =
                new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.50));
        when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class))).thenReturn(java.util.List.of());

        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.OWNER, VehicleFamilyResolver.ALL);
        config.retrieverFor(filter).retrieve(new Query("maintenance schedule"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString())
                .doesNotContain("vehicle_family");
    }

    @Test
    void specificVehicleFamilyIncludesMatchingAndGeneralChunks() {
        VectorStoreDocumentRetrieverConfig config =
                new VectorStoreDocumentRetrieverConfig(vectorStore, propertiesWithThreshold(0.50));
        when(vectorStore.similaritySearch(Mockito.any(SearchRequest.class))).thenReturn(java.util.List.of());

        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.OWNER, "AC-EV-1");
        config.retrieverFor(filter).retrieve(new Query("maintenance schedule"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString())
                .contains("AC-EV-1")
                .contains(VehicleFamilyResolver.ALL);
    }
}
