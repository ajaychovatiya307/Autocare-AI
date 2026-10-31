package com.autocare.ai.transform;

import com.autocare.ai.advisor.ConversationAuditAdvisor;
import com.autocare.ai.service.LanguageDetectionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.Locale;

@Configuration(proxyBeanMethods = false)
public class TranslationQueryTransformerConfig {

    @Bean
    public QueryTransformer translationQueryTransformer(
            @Qualifier("queryTransformChatClient") ChatClient queryTransformChatClient,
            @Qualifier("translationPromptTemplate") PromptTemplate translationPromptTemplate,
            LanguageDetectionService languageDetectionService,
            @Value("${autocare.rag.retrieval-language:en}") String retrievalLanguage) {
        Assert.hasText(retrievalLanguage, "autocare.rag.retrieval-language must not be blank");
        String targetLanguage = Locale.forLanguageTag(retrievalLanguage.trim()).getLanguage();
        Assert.isTrue(!targetLanguage.isBlank() && !"und".equals(targetLanguage),
                "autocare.rag.retrieval-language must be a valid BCP-47 language tag");

        TranslationQueryTransformer translator = TranslationQueryTransformer.builder()
                .chatClientBuilder(queryTransformChatClient.mutate())
                .promptTemplate(translationPromptTemplate)
                .targetLanguage(targetLanguage)
                .build();

        return query -> {
            Assert.notNull(query, "query must not be null");
            String sourceLanguage = languageDetectionService.detectLanguage(query.text());
            Query tracedQuery = QueryTransformationTrace.recordLanguage(query, sourceLanguage);
            return "und".equals(sourceLanguage)
                            || targetLanguage.equalsIgnoreCase(
                                    Locale.forLanguageTag(sourceLanguage).getLanguage())
                    ? tracedQuery
                    : recordTranslation(tracedQuery, translator);
        };
    }

    private Query recordTranslation(Query query, TranslationQueryTransformer translator) {
        Query translated = translator.transform(query);
        return QueryTransformationTrace.recordTransformation(
                translated, ConversationAuditAdvisor.CTX_TRANSLATION_APPLIED, translated.text());
    }
}
