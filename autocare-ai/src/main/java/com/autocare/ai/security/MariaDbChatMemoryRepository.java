package com.autocare.ai.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class MariaDbChatMemoryRepository implements ChatMemoryRepository {

    private static final String TABLE = "SPRING_AI_CHAT_MEMORY";
    private final JdbcTemplate jdbcTemplate;

    public MariaDbChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.query(
                "SELECT DISTINCT conversation_id FROM " + TABLE + " ORDER BY conversation_id",
                (resultSet, rowNumber) -> resultSet.getString("conversation_id"));
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(
                "SELECT type, content FROM " + TABLE
                        + " WHERE conversation_id = ? ORDER BY `timestamp`, id",
                (resultSet, rowNumber) -> toMessage(
                        resultSet.getString("type"), resultSet.getString("content")),
                conversationId);
    }

    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE conversation_id = ?", conversationId);
        Timestamp timestamp = Timestamp.from(Instant.now());
        for (Message message : messages) {
            jdbcTemplate.update(
                    "INSERT INTO " + TABLE + " (conversation_id, content, type, `timestamp`) VALUES (?, ?, ?, ?)",
                    conversationId,
                    message.getText(),
                    message.getMessageType().getValue(),
                    timestamp);
        }
    }

    @Override
    @Transactional
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE conversation_id = ?", conversationId);
    }

    private Message toMessage(String type, String content) {
        if ("USER".equalsIgnoreCase(type)) {
            return new UserMessage(content);
        }
        if ("ASSISTANT".equalsIgnoreCase(type)) {
            return new AssistantMessage(content);
        }
        if ("SYSTEM".equalsIgnoreCase(type)) {
            return new SystemMessage(content);
        }
        throw new IllegalStateException("Unsupported persisted chat memory message type.");
    }
}
