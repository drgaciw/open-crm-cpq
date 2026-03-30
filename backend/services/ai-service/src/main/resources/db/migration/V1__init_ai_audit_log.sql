CREATE SCHEMA IF NOT EXISTS ai;
SET search_path TO ai;

CREATE TABLE ai_audit_log (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    agent_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    details TEXT
);
