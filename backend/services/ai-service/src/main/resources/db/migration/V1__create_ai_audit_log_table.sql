-- Set search path to ai schema
SET search_path TO ai;

CREATE TABLE ai_audit_log (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    agent_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    input_context JSONB,
    llm_output TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_ai_audit_log_agent_session ON ai_audit_log(agent_id, session_id);
