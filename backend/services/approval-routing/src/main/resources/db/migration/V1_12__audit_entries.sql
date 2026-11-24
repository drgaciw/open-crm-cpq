-- Rollback: db/rollback/V1_12__drop_audit_entries.sql
-- DRG-442 / BC-20-01: append-only audit trail in approval-routing, same shape as crm-service V20.
-- The library owns no schema. UPDATE and DELETE are rejected by trigger.

CREATE TABLE audit_entries (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(128),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    principal VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    previous_hash VARCHAR(64) NOT NULL,
    entry_hash VARCHAR(64) NOT NULL UNIQUE
);

CREATE INDEX idx_audit_entries_tenant ON audit_entries (tenant_id);
CREATE INDEX idx_audit_entries_aggregate ON audit_entries (aggregate_type, aggregate_id);
CREATE INDEX idx_audit_entries_principal ON audit_entries (principal);
CREATE INDEX idx_audit_entries_occurred_at ON audit_entries (occurred_at);

CREATE FUNCTION reject_audit_entries_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_entries_no_update
    BEFORE UPDATE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION reject_audit_entries_mutation();

CREATE TRIGGER trg_audit_entries_no_delete
    BEFORE DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION reject_audit_entries_mutation();
