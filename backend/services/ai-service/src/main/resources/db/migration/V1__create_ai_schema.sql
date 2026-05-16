-- ============================================================
-- AI Service — Flyway V1 Migration
-- Schema: ai
-- Tables: agent_audit_log, agent_session
-- LLM: GLM-5 via Zhipu AI (ADR-002)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ai;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- agent_audit_log: immutable record of every agent invocation and response.
-- Implements the audit trail requirement from agent-prd.md Section 2.3.
CREATE TABLE IF NOT EXISTS ai.agent_audit_log (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    agent_id        VARCHAR(50)     NOT NULL,               -- e.g. cpq-agent-004
    session_id      VARCHAR(100)    NOT NULL,               -- caller-supplied or generated UUID
    event_type      VARCHAR(20)     NOT NULL,               -- INVOCATION | RESPONSE | ERROR
    input_context   JSONB,                                  -- serialized AgentRequest.context
    snapshot_references JSONB,                              -- durable references to quote/catalog/pricing snapshots used by the invocation
    llm_output      TEXT,                                   -- raw LLM response text
    human_decision  VARCHAR(20),                            -- ACCEPTED | REJECTED | MODIFIED | PENDING
    latency_ms      INTEGER,                                -- end-to-end invocation latency
    model           VARCHAR(100)    DEFAULT 'glm-5',        -- LLM model used (ADR-002)
    created_by      VARCHAR(255),                           -- invoking user (extracted from JWT)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pk_agent_audit_log PRIMARY KEY (id)
);

-- Indexes for common query patterns (agent dashboard, compliance reports)
CREATE INDEX IF NOT EXISTS idx_audit_agent_id    ON ai.agent_audit_log (agent_id);
CREATE INDEX IF NOT EXISTS idx_audit_session_id  ON ai.agent_audit_log (session_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at  ON ai.agent_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_type  ON ai.agent_audit_log (event_type);

-- ============================================================
-- agent_session: tracks multi-turn agent conversation context.
-- Mirrors the TTL-based Redis session (ai:session:{sessionId}) with
-- a durable PostgreSQL record for compliance and replay.
-- ============================================================
CREATE TABLE IF NOT EXISTS ai.agent_session (
    session_id      VARCHAR(100)    NOT NULL,
    agent_id        VARCHAR(50)     NOT NULL,
    state           JSONB,                                  -- serialized conversation state
    turn_count      INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT now() + INTERVAL '24 hours',

    CONSTRAINT pk_agent_session PRIMARY KEY (session_id, agent_id)
);

CREATE INDEX IF NOT EXISTS idx_session_agent_id  ON ai.agent_session (agent_id);
CREATE INDEX IF NOT EXISTS idx_session_expires   ON ai.agent_session (expires_at);

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE  ai.agent_audit_log IS 'Immutable audit trail for all CPQ AI agent invocations (ADR-002)';
COMMENT ON COLUMN ai.agent_audit_log.event_type     IS 'INVOCATION=request received; RESPONSE=LLM replied; ERROR=exception';
COMMENT ON COLUMN ai.agent_audit_log.snapshot_references IS 'Snapshot identifiers supplied with AgentRequest.context.snapshotReferences';
COMMENT ON COLUMN ai.agent_audit_log.human_decision IS 'Set by UI after human reviews: ACCEPTED|REJECTED|MODIFIED|PENDING';
COMMENT ON COLUMN ai.agent_audit_log.model          IS 'LLM model used — glm-5 per ADR-002';

COMMENT ON TABLE  ai.agent_session IS 'Durable session state mirroring Redis ai:session:{id} with 24h TTL';
