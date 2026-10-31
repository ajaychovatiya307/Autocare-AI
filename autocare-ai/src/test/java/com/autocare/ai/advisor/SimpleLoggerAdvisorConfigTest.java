package com.autocare.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.core.Ordered;

class SimpleLoggerAdvisorConfigTest {

    private final SimpleLoggerAdvisorConfig config = new SimpleLoggerAdvisorConfig();

    @Test
    void registersSimpleLoggerAdvisorBean() {
        SimpleLoggerAdvisor advisor = config.simpleLoggerAdvisor();

        assertThat(advisor).isNotNull();
    }

    @Test
    void ordersAfterConversationAuditAdvisor() {
        SimpleLoggerAdvisor advisor = config.simpleLoggerAdvisor();

        assertThat(advisor.getOrder()).isEqualTo(SimpleLoggerAdvisorConfig.ORDER);
        assertThat(advisor.getOrder()).isGreaterThan(ConversationAuditAdvisor.ORDER);
        assertThat(advisor.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
    }
}
