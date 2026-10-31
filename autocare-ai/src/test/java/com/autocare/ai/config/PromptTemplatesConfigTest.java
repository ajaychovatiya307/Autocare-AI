package com.autocare.ai.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PromptTemplatesConfig.class);

    @Test
    void loadsAndRendersEveryPromptResource() {
        contextRunner.run(context -> {
            PromptTemplate answer = context.getBean("answerPromptTemplate", PromptTemplate.class);
            PromptTemplate translation = context.getBean("translationPromptTemplate", PromptTemplate.class);
            PromptTemplate rewrite = context.getBean("rewritePromptTemplate", PromptTemplate.class);
            PromptTemplate expansion = context.getBean("expansionPromptTemplate", PromptTemplate.class);

            assertThat(answer.render(Map.of("role", "OWNER", "language", "English")))
                    .contains("Authenticated user role: OWNER")
                    .contains("Answer in the user's language (English)")
                    .contains("available conversation history to answer what was asked or said")
                    .contains("Conversation history may clarify the user's intent")
                    .contains("(docCode: value, version: value, section: value)")
                    .contains("Treat the verified scope facts as approved Knowledge Base content")
                    .contains("CityHatch P (AC-HB-P, petrol; Normal or Severe usage)")
                    .contains("booking and workshop visits")
                    .contains("(docCode: KB-AUTO-001, version: v1.0, section: 2)")
                    .contains("preliminary pre-check only");
            assertThat(translation.render(Map.of("targetLanguage", "English", "query", "service interval")))
                    .contains("Translate the user's query to English")
                    .contains("service interval");
            assertThat(rewrite.render(Map.of("target", "AutoCare Knowledge Base", "query", "follow-up")))
                    .contains("Rewrite the user's follow-up")
                    .contains("follow-up");
            assertThat(expansion.render(Map.of("number", 3, "query", "service schedule")))
                    .contains("Generate exactly 3 alternative search queries")
                    .contains("service schedule");
        });
    }
}
