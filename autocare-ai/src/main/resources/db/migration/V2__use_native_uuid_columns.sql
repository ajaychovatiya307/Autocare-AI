ALTER TABLE conversation_audit_record
    MODIFY COLUMN session_id UUID NOT NULL,
    MODIFY COLUMN correlation_id UUID NOT NULL;
