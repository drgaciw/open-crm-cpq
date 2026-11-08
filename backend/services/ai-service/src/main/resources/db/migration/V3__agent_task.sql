-- Durable agent work queue (ADR-029 D-01). Create only; existing tables stay as they are.
-- tenant_id is an opaque caller-supplied string. This migration does not parse or bound it.

CREATE TABLE ai.agent_task (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL,
    kind            TEXT        NOT NULL,
    subject_type    TEXT        NOT NULL,
    subject_id      TEXT        NOT NULL,
    reason          TEXT,
    payload         JSONB,
    priority        INTEGER     NOT NULL DEFAULT 0,
    budget          INTEGER,
    attempts        INTEGER     NOT NULL DEFAULT 0,
    max_attempts    INTEGER     NOT NULL,
    due_at          TIMESTAMPTZ NOT NULL,
    leased_until    TIMESTAMPTZ,
    lease_owner     TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    outcome         TEXT,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_agent_task PRIMARY KEY (id),
    CONSTRAINT ck_agent_task_budget_non_negative CHECK (budget IS NULL OR budget >= 0),
    CONSTRAINT ck_agent_task_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT ck_agent_task_max_attempts_positive CHECK (max_attempts >= 1)
);

CREATE UNIQUE INDEX uq_agent_task_unfinished
    ON ai.agent_task (tenant_id, kind, subject_type, subject_id)
    WHERE finished_at IS NULL;

CREATE INDEX idx_agent_task_claim_due
    ON ai.agent_task (due_at)
    WHERE finished_at IS NULL;

COMMENT ON COLUMN ai.agent_task.tenant_id IS
    'Opaque caller-supplied tenant string. Not converted or length-checked by this queue.';
