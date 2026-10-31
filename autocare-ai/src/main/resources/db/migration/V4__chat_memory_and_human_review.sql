CREATE TABLE SPRING_AI_CHAT_MEMORY (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL,
    `timestamp` TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_memory_conversation_timestamp (conversation_id, `timestamp`)
);

CREATE TABLE answer_review_queue (
    id UUID NOT NULL,
    session_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    redacted_query TEXT NOT NULL,
    proposed_answer TEXT NOT NULL,
    final_answer TEXT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_status_created (status, created_at),
    KEY idx_review_session (session_id)
);

CREATE TABLE answer_review_decision_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id UUID NOT NULL,
    session_id UUID NOT NULL,
    reviewer VARCHAR(128) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason TEXT NULL,
    final_answer TEXT NULL,
    reviewed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_decision_reviewed (review_id, reviewed_at)
);
