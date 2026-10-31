package com.autocare.ai.config;

import com.autocare.ai.security.RedactingChatMemoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.Map;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMemoryConfigTest {

    @Test
    void memoryAdvisorAddsEarlierContextUsingTheCurrentSessionId() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        memory.add("session-a", new UserMessage("What service is due?"));
        memory.add("session-a", new AssistantMessage("The retrieved schedule says Service A is due."));
        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(memory).build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What about its warranty?")))
                .context(Map.of(ChatMemory.CONVERSATION_ID, "session-a"))
                .build();

        ChatClientRequest enriched = advisor.before(request, Mockito.mock(AdvisorChain.class));

        assertThat(enriched.prompt().getInstructions())
                .extracting(Message::getText)
                .containsExactly(
                        "What service is due?",
                        "The retrieved schedule says Service A is due.",
                        "What about its warranty?");
    }

    @Test
    void retainsContextPerSessionAndEvictsMessagesPastTheWindow() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        String firstSession = "session-a";
        String secondSession = "session-b";
        memory.add(firstSession, new UserMessage("earlier service question"));
        memory.add(firstSession, new AssistantMessage("earlier grounded answer"));

        assertThat(memory.get(firstSession)).extracting(Message::getText)
                .containsExactly("earlier service question", "earlier grounded answer");
        assertThat(memory.get(secondSession)).isEmpty();

        for (int index = 0; index < 21; index++) {
            memory.add(secondSession, new UserMessage("message-" + index));
        }
        assertThat(memory.get(secondSession)).hasSize(20);
        assertThat(memory.get(secondSession).get(0).getText()).isEqualTo("message-1");
        assertThat(memory.get(secondSession).get(19).getText()).isEqualTo("message-20");
    }

    @Test
    void redactsBothIncomingAndRetrievedMemoryText() {
        ChatMemoryRepository repository =
                new RedactingChatMemoryRepository(new InMemoryChatMemoryRepository());
        repository.saveAll(
                "session-a",
                List.of(
                        new UserMessage("VIN 1HGCM82633A004352"),
                        new AssistantMessage("Call 555-123-4567")));

        List<Message> stored = repository.findByConversationId("session-a");
        assertThat(stored.get(0).getText()).contains("[VIN-REDACTED]").doesNotContain("1HGCM82633A004352");
        assertThat(stored.get(1).getText()).contains("[PHONE-REDACTED]").doesNotContain("555-123-4567");
    }
}
