package com.autocare.ai.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingProperties;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorStoreConfigTest {

    private final OpenAIClientBuilder openAIClientBuilder = mock(OpenAIClientBuilder.class);
    private final OpenAIClient openAIClient = mock(OpenAIClient.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withBean(OpenAIClientBuilder.class, () -> openAIClientBuilder)
                    .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                    .withPropertyValues(
                            "autocare.embedding.dimension=3072",
                            "autocare.vector-store.initialize-schema=false",
                            "autocare.vector-store.schema-validation=false",
                            "spring.ai.azure.openai.embedding.options.deployment-name=test-embedding");

    @BeforeEach
    void setUp() {
        when(openAIClientBuilder.buildClient()).thenReturn(openAIClient);
    }

    @Test
    void configuresAzureEmbeddingAndMariaDbVectorStoreWithMatchingDimensions() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context).hasSingleBean(VectorStore.class);

            AzureOpenAiEmbeddingModel embeddingModel = context.getBean(AzureOpenAiEmbeddingModel.class);
            assertThat(embeddingModel.getDefaultOptions().getDeploymentName()).isEqualTo("test-embedding");
            assertThat(embeddingModel.getDefaultOptions().getDimensions()).isEqualTo(3072);

            MariaDBVectorStore vectorStore = context.getBean(MariaDBVectorStore.class);
            assertThat(ReflectionTestUtils.getField(vectorStore, "dimensions")).isEqualTo(3072);
        });
    }

    @Test
    void doesNotSendUnsupportedDimensionsOptionToAda002() {
        contextRunner
                .withPropertyValues(
                        "autocare.embedding.dimension=1536",
                        "spring.ai.azure.openai.embedding.options.deployment-name=text-embedding-ada-002")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AzureOpenAiEmbeddingModel model = context.getBean(AzureOpenAiEmbeddingModel.class);
                    assertThat(model.getDefaultOptions().getDeploymentName())
                            .isEqualTo("text-embedding-ada-002");
                    assertThat(model.getDefaultOptions().getDimensions()).isNull();
                });
    }

    @Test
    void rejectsConfiguredDimensionThatDoesNotMatchAda002Output() {
        contextRunner
                .withPropertyValues("spring.ai.azure.openai.embedding.options.deployment-name=text-embedding-ada-002")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "text-embedding-ada-002 returns 1536 dimensions; autocare.embedding.dimension "
                                            + "must be 1536");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AutoCareProperties.class, AzureOpenAiEmbeddingProperties.class})
    @Import(VectorStoreConfig.class)
    static class TestConfig {
    }
}
