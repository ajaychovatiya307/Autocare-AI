package com.autocare.ai.config;

import com.azure.ai.openai.OpenAIClientBuilder;
import java.util.Locale;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingProperties;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

    @Bean
    public AzureOpenAiEmbeddingModel embeddingModel(
            OpenAIClientBuilder openAIClientBuilder,
            AzureOpenAiEmbeddingProperties embeddingProperties,
            AutoCareProperties properties) {
        int dimension = properties.embedding().dimension();
        Assert.isTrue(dimension > 0, "autocare.embedding.dimension must be greater than zero");

        String deploymentName = embeddingProperties.getOptions().getDeploymentName();
        Assert.hasText(deploymentName, "Azure OpenAI embedding deployment-name must be configured");
        AzureOpenAiEmbeddingOptions.Builder optionsBuilder =
                AzureOpenAiEmbeddingOptions.builder().from(embeddingProperties.getOptions());
        if (deploymentName.toLowerCase(Locale.ROOT).contains("ada-002")) {
            Assert.isTrue(
                    dimension == 1536,
                    "text-embedding-ada-002 returns 1536 dimensions; autocare.embedding.dimension must be 1536");
        } else {
            optionsBuilder.dimensions(dimension);
        }
        AzureOpenAiEmbeddingOptions embeddingOptions = optionsBuilder.build();

        return new AzureOpenAiEmbeddingModel(
                openAIClientBuilder.buildClient(),
                embeddingProperties.getMetadataMode(),
                embeddingOptions);
    }

    @Bean
    public MariaDBVectorStore vectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            AutoCareProperties properties,
            @Value("${autocare.vector-store.initialize-schema:true}") boolean initializeSchema,
            @Value("${autocare.vector-store.schema-validation:true}") boolean schemaValidation) {
        int dimension = properties.embedding().dimension();
        Assert.isTrue(dimension > 0, "autocare.embedding.dimension must be greater than zero");

        return MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimension)
                .initializeSchema(initializeSchema)
                .schemaValidation(schemaValidation)
                .build();
    }
}
