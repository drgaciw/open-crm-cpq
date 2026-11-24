-- Rollback: db/rollback/V1_13__drop_policy_exceptions.sql
-- DRG-445 / BC-04-05. Time-boxed policy exceptions bound to one quote version and one policy.
-- Does not alter approval_requests. H4 owns tenant_id on approval rows.
-- tenant_id on this new table is the caller tenant copied at record time so evaluation cannot
-- honor another tenant's exception.

CREATE TABLE policy_exceptions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(128) NOT NULL,
    quote_id VARCHAR(64) NOT NULL,
    quote_version BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    justification TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    recorded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_policy_exception_policy FOREIGN KEY (policy_id) REFERENCES commercial_policies (id),
    CONSTRAINT chk_policy_exception_justification CHECK (length(btrim(justification)) > 0)
);

CREATE INDEX idx_policy_exception_quote_version ON policy_exceptions (quote_id, quote_version);
CREATE INDEX idx_policy_exception_policy ON policy_exceptions (policy_id);
CREATE INDEX idx_policy_exception_tenant_quote ON policy_exceptions (tenant_id, quote_id, quote_version);
