-- DRG-309 (BC-20-01): append-only, hash-chained audit trail.
--
-- Placement: crm-service is the host for this story's SearchAuditTrail REST surface and the audit_entries
-- table (it already has a database connection and a REST layer; there is no dedicated audit-trail service).
-- The AuditEntry entity, repository and AuditTrailService themselves live in the shared libs/audit library so
-- other services can adopt the same table/trigger shape later without depending on crm-service; see that
-- library's Javadoc. This table is not a CRM domain table and has no foreign keys into crm-service's own data.
--
-- Immutability is enforced in three independent layers:
--   1. JPA: every AuditEntry column is mapped updatable = false, so Hibernate never issues an UPDATE.
--   2. Repository: AuditEntryRepository (libs/audit) extends the bare Spring Data Repository marker and
--      declares only save/findBy* - no delete or update method exists to call.
--   3. Database (this migration): a trigger rejects UPDATE and DELETE outright, independent of the two layers
--      above and of which role or tool issues the SQL.

SET search_path TO crm;

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

COMMENT ON TABLE audit_entries IS
    'DRG-309: append-only, hash-chained audit trail. UPDATE/DELETE are rejected by trigger - see reject_audit_entries_mutation().';
COMMENT ON COLUMN audit_entries.previous_hash IS
    'entry_hash of the previous row in the chain (ordered by id), or 64 zero characters for the first entry.';
COMMENT ON COLUMN audit_entries.entry_hash IS
    'SHA-256 (hex) of this row''s own content plus previous_hash. Computed by AuditTrailService, never by the database.';
