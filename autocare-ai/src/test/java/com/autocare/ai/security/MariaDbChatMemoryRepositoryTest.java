package com.autocare.ai.security;

import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MariaDbChatMemoryRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private MariaDbChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:chat-memory-" + System.nanoTime() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE SPRING_AI_CHAT_MEMORY (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    conversation_id VARCHAR(36) NOT NULL,
                    content TEXT NOT NULL,
                    type VARCHAR(10) NOT NULL,
                    `timestamp` TIMESTAMP(6) NOT NULL
                )
                """);
        repository = new MariaDbChatMemoryRepository(jdbcTemplate);
    }

    @Test
    void persistsMessagesAndKeepsConversationsIsolated() {
        repository.saveAll(
                "session-a",
                List.of(new UserMessage("question"), new AssistantMessage("answer")));
        List<Message> messages = repository.findByConversationId("session-a");
        assertThat(messages).extracting(Message::getText)
                .containsExactly("question", "answer");
        assertThat(repository.findByConversationId("session-b")).isEmpty();
        assertThat(repository.findConversationIds()).containsExactly("session-a");

        repository.deleteByConversationId("session-a");
        assertThat(repository.findByConversationId("session-a")).isEmpty();
    }

    @Test
    void replacesExistingMessagesForConversation() {
        repository.saveAll("session-a", List.of(new UserMessage("old")));
        repository.saveAll("session-a", List.of(new UserMessage("new")));
        assertThat(repository.findByConversationId("session-a"))
                .extracting(Message::getText)
                .containsExactly("new");
    }

}
