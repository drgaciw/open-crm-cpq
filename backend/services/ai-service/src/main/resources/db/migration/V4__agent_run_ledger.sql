-- DRG-523: local PostgreSQL ledger. Agent Kafka #241 remains held.
-- Tenant strings follow V3 exactly; isolation is enforced by predicates and composite FKs, not RLS.
ALTER TABLE ai.agent_task ADD CONSTRAINT uq_agent_task_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE ai.agent_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    task_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens BIGINT NOT NULL DEFAULT 0 CHECK (prompt_tokens >= 0),
    completion_tokens BIGINT NOT NULL DEFAULT 0 CHECK (completion_tokens >= 0),
    total_tokens BIGINT NOT NULL DEFAULT 0 CHECK (total_tokens = prompt_tokens + completion_tokens),
    cost_usd NUMERIC(24,12) NOT NULL DEFAULT 0 CHECK (cost_usd >= 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    latency_ms BIGINT CHECK (latency_ms >= 0),
    error_code TEXT,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, task_id) REFERENCES ai.agent_task (tenant_id, id),
    CHECK ((status = 'RUNNING' AND finished_at IS NULL AND latency_ms IS NULL)
        OR (status <> 'RUNNING' AND finished_at IS NOT NULL AND finished_at >= started_at AND latency_ms IS NOT NULL))
);
CREATE INDEX idx_agent_run_tenant_task ON ai.agent_run (tenant_id, task_id, started_at DESC);

CREATE TABLE ai.agent_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    run_id UUID NOT NULL,
    kind TEXT NOT NULL,
    tool TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_tokens BIGINT NOT NULL CHECK (prompt_tokens >= 0),
    completion_tokens BIGINT NOT NULL CHECK (completion_tokens >= 0),
    total_tokens BIGINT NOT NULL CHECK (total_tokens = prompt_tokens + completion_tokens),
    input_usd_per_million NUMERIC(24,12) NOT NULL CHECK (input_usd_per_million >= 0),
    output_usd_per_million NUMERIC(24,12) NOT NULL CHECK (output_usd_per_million >= 0),
    cost_usd NUMERIC(24,12) NOT NULL CHECK (cost_usd >= 0),
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, run_id) REFERENCES ai.agent_run (tenant_id, id)
);
CREATE INDEX idx_agent_action_run ON ai.agent_action (tenant_id, run_id, created_at);

CREATE TABLE ai.agent_audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    run_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    actor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, run_id) REFERENCES ai.agent_run (tenant_id, id)
);
CREATE INDEX idx_agent_audit_event_run ON ai.agent_audit_event (tenant_id, run_id, created_at);

CREATE FUNCTION ai.reject_ledger_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger records are append-only' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER agent_action_append_only BEFORE UPDATE OR DELETE ON ai.agent_action
    FOR EACH ROW EXECUTE FUNCTION ai.reject_ledger_mutation();
CREATE TRIGGER agent_audit_event_append_only BEFORE UPDATE OR DELETE ON ai.agent_audit_event
    FOR EACH ROW EXECUTE FUNCTION ai.reject_ledger_mutation();
CREATE TRIGGER agent_action_no_truncate BEFORE TRUNCATE ON ai.agent_action
    EXECUTE FUNCTION ai.reject_ledger_mutation();
CREATE TRIGGER agent_audit_event_no_truncate BEFORE TRUNCATE ON ai.agent_audit_event
    EXECUTE FUNCTION ai.reject_ledger_mutation();
CREATE TRIGGER agent_run_no_truncate BEFORE TRUNCATE ON ai.agent_run
    EXECUTE FUNCTION ai.reject_ledger_mutation();

CREATE FUNCTION ai.guard_agent_run() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'runs cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF OLD.status <> 'RUNNING' OR NEW.id <> OLD.id OR NEW.tenant_id <> OLD.tenant_id
        OR NEW.task_id <> OLD.task_id OR NEW.started_at <> OLD.started_at
        OR NEW.provider <> OLD.provider OR NEW.model <> OLD.model THEN
        RAISE EXCEPTION 'run identity and terminal runs are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER agent_run_immutable BEFORE UPDATE OR DELETE ON ai.agent_run
    FOR EACH ROW EXECUTE FUNCTION ai.guard_agent_run();

COMMENT ON COLUMN ai.agent_run.cost_usd IS 'USD, scale 12; sum of rounded action charges, no floating point';
COMMENT ON COLUMN ai.agent_run.provider IS 'unknown for handler-only runs; metered action records carry actual provider/model';
COMMENT ON TABLE ai.agent_audit_event IS 'Append-only local lifecycle metadata; no prompts, completions or customer payloads';
