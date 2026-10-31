package com.autocare.ai.config;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
public class PromptTemplatesConfig {

    @Bean
    @Qualifier("answerPromptTemplate")
    public PromptTemplate answerPromptTemplate(@Value("classpath:prompts/answer.st") Resource resource) {
        return new PromptTemplate(resource);
    }

    @Bean
    @Qualifier("translationPromptTemplate")
    public PromptTemplate translationPromptTemplate(@Value("classpath:prompts/translate.st") Resource resource) {
        return new PromptTemplate(resource);
    }

    @Bean
    @Qualifier("rewritePromptTemplate")
    public PromptTemplate rewritePromptTemplate(@Value("classpath:prompts/rewrite.st") Resource resource) {
        return new PromptTemplate(resource);
    }

    @Bean
    @Qualifier("expansionPromptTemplate")
    public PromptTemplate expansionPromptTemplate(@Value("classpath:prompts/expand.st") Resource resource) {
        return new PromptTemplate(resource);
    }
}
