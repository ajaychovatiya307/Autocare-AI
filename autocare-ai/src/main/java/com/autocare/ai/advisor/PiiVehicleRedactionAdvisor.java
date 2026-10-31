package com.autocare.ai.advisor;

import com.autocare.ai.security.RedactionPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Advisor chain stage 1 (architecture.md §17.1): redacts PII/vehicle identifiers from the raw
 * query before it reaches any downstream advisor, logging or model call, per SRS §2.1/§4.6, NFR-003.
 */
@Component
public class PiiVehicleRedactionAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PiiVehicleRedactionAdvisor.class);

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        Prompt prompt = request.prompt();
        if (prompt.getUserMessages().isEmpty()) {
            logger.debug("advisor={} action=skipped reason=no_user_message", getName());
            return request;
        }
        Prompt redactedPrompt = prompt.augmentUserMessage(
                userMessage -> userMessage.mutate()
                        .text(RedactionPatterns.redact(userMessage.getText()))
                        .build());
        logger.info("advisor={} action=redaction_applied", getName());
        return request.mutate().prompt(redactedPrompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        return response;
    }

    @Override
    public String getName() {
        return PiiVehicleRedactionAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
