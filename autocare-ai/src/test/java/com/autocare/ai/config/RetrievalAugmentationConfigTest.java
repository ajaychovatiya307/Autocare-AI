package com.autocare.ai.config;

import com.autocare.ai.exception.NoMatchingContextException;
import com.autocare.ai.model.enums.DocStatus;
import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.retrieval.MetadataFilterBuilder;
import com.autocare.ai.retrieval.VectorStoreDocumentRetrieverConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RetrievalAugmentationConfigTest {

    private final QueryTransformer identityTransformer = query -> query;
    private final QueryExpander singleQueryExpander = List::of;
    private final QueryAugmenter augmenter = (query, documents) -> {
        if (documents == null || documents.isEmpty()) {
            throw new NoMatchingContextException();
        }
        return query;
    };
    private final VectorStoreDocumentRetrieverConfig retrieverConfig =
            Mockito.mock(VectorStoreDocumentRetrieverConfig.class);
    private final AdvisorChain advisorChain = Mockito.mock(AdvisorChain.class);

    private RetrievalAugmentationConfig config;

    @BeforeEach
    void setUp() {
        config = new RetrievalAugmentationConfig(
                identityTransformer, identityTransformer, singleQueryExpander, retrieverConfig, augmenter);
    }

    private ChatClientRequest requestWithQuery(String text) {
        return ChatClientRequest.builder().prompt(new Prompt(new UserMessage(text))).context(Map.of()).build();
    }

    @Test
    void defaultAdvisorUsesUnfilteredRetriever() {
        VectorStoreDocumentRetriever retriever = Mockito.mock(VectorStoreDocumentRetriever.class);
        when(retriever.retrieve(any(Query.class)))
                .thenReturn(List.of(new Document("Standard Service B is due at 20,000 km.")));
        when(retrieverConfig.vectorStoreDocumentRetriever()).thenReturn(retriever);

        RetrievalAugmentationAdvisor advisor = config.retrievalAugmentationAdvisor();
        ChatClientRequest result = advisor.before(requestWithQuery("What is due?"), advisorChain);

        assertThat(result).isNotNull();
    }

    @Test
    void perRequestAdvisorUsesFilteredRetrieverAndEscalatesOnEmptyContext() {
        VectorStoreDocumentRetriever retriever = Mockito.mock(VectorStoreDocumentRetriever.class);
        when(retriever.retrieve(any(Query.class))).thenReturn(List.of());
        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.ADVISOR, "AC-SD-P");
        when(retrieverConfig.retrieverFor(filter)).thenReturn(retriever);

        RetrievalAugmentationAdvisor advisor = config.advisorFor(filter);

        assertThatThrownBy(() -> advisor.before(requestWithQuery("Unsupported vehicle query"), advisorChain))
                .isInstanceOf(NoMatchingContextException.class);
    }

    @Test
    void perRequestAdvisorReturnsGroundedResultWhenContextIsPresent() {
        VectorStoreDocumentRetriever retriever = Mockito.mock(VectorStoreDocumentRetriever.class);
        when(retriever.retrieve(any(Query.class)))
                .thenReturn(List.of(new Document("Standard Service B is due at 20,000 km.")));
        MetadataFilterBuilder.Filter filter =
                new MetadataFilterBuilder.Filter(DocStatus.ACTIVE, "v1.0", UserRole.OWNER, "AC-SD-P");
        when(retrieverConfig.retrieverFor(filter)).thenReturn(retriever);

        RetrievalAugmentationAdvisor advisor = config.advisorFor(filter);
        ChatClientRequest result = advisor.before(requestWithQuery("What is due at 20,000 km?"), advisorChain);

        assertThat(result).isNotNull();
    }
}
