CREATE TABLE IF NOT EXISTS ai_audit_log (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    agent_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    input_context TEXT,
    llm_output TEXT
);

CREATE INDEX idx_ai_audit_log_session_id ON ai_audit_log(session_id);
CREATE INDEX idx_ai_audit_log_agent_id ON ai_audit_log(agent_id);
