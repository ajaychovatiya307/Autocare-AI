package com.autocare.ai.advisor;

import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Advisor chain stages 3 and 14 (architecture.md §17.3/§17.14): registers the framework-provided
 * {@link SimpleLoggerAdvisor} for request/response diagnostic logging, distinct from the persistent,
 * redacted {@link ConversationAuditAdvisor} business record (SRS §2.4). It runs immediately after
 * {@link ConversationAuditAdvisor} opens the audit record, so it only ever logs the already-redacted
 * query (NFR-003, NFR-009).
 */
@Configuration(proxyBeanMethods = false)
public class SimpleLoggerAdvisorConfig {

    public static final int ORDER = ConversationAuditAdvisor.ORDER + 100;

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return SimpleLoggerAdvisor.builder().order(ORDER).build();
    }
}
