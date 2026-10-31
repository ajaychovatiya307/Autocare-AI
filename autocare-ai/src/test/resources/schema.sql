CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL,
    `timestamp` TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_memory_conversation_timestamp (conversation_id, `timestamp`)
);
