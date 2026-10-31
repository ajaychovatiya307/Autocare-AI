CREATE TABLE IF NOT EXISTS conversation_audit_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BINARY(16) NOT NULL,
    turn_seq INTEGER NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    `timestamp` TIMESTAMP(6) NOT NULL,
    original_query TEXT NOT NULL,
    redacted_query TEXT NOT NULL,
    language_detected VARCHAR(32),
    translation_applied BOOLEAN NOT NULL,
    rewrite_applied BOOLEAN NOT NULL,
    expansion_applied BOOLEAN NOT NULL,
    transformed_query TEXT NOT NULL,
    expanded_queries TEXT,
    retrieved_doc_codes TEXT,
    retrieved_chunk_refs TEXT,
    query_chat_client VARCHAR(128),
    answer_chat_client VARCHAR(128) NOT NULL,
    response_text TEXT NOT NULL,
    citation_status VARCHAR(16) NOT NULL,
    escalation_status BOOLEAN NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doc_code VARCHAR(32) NOT NULL,
    version VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    audience VARCHAR(64) NOT NULL,
    vehicle_family VARCHAR(32),
    section VARCHAR(32),
    doc_type VARCHAR(64),
    content TEXT NOT NULL,
    embedding TEXT NOT NULL,
    PRIMARY KEY (id)
);
