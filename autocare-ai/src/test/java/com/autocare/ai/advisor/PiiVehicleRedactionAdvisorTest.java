package com.autocare.ai.advisor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PiiVehicleRedactionAdvisorTest {

    private final PiiVehicleRedactionAdvisor advisor = new PiiVehicleRedactionAdvisor();
    private final AdvisorChain chain = Mockito.mock(AdvisorChain.class);

    @Test
    void redactsPiiFromUserMessageBeforeDownstreamAdvisors() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage(
                        "My VIN is 1HGCM82633A004352, email jane.doe@example.com, call +1 415-555-0132")))
                .context(Map.of())
                .build();

        ChatClientRequest result = advisor.before(request, chain);

        String redactedText = result.prompt().getUserMessage().getText();
        assertThat(redactedText)
                .doesNotContain("1HGCM82633A004352")
                .doesNotContain("jane.doe@example.com")
                .doesNotContain("415-555-0132")
                .contains("[VIN-REDACTED]")
                .contains("[EMAIL-REDACTED]")
                .contains("[PHONE-REDACTED]");
    }

    @Test
    void passesThroughRequestUnchangedWhenNoUserMessagePresent() {
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(java.util.List.of()))
                .context(Map.of())
                .build();

        ChatClientRequest result = advisor.before(request, chain);

        assertThat(result).isSameAs(request);
    }

    @Test
    void afterIsPassThrough() {
        var response = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClientResponse.class);

        assertThat(advisor.after(response, chain)).isSameAs(response);
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(advisor.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
