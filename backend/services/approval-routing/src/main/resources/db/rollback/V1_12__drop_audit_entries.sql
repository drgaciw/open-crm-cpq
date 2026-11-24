-- Manual rollback of V1_12__audit_entries.sql. Run with search_path = approval.
SET search_path TO approval;

DROP TRIGGER IF EXISTS trg_audit_entries_no_update ON audit_entries;
DROP TRIGGER IF EXISTS trg_audit_entries_no_delete ON audit_entries;
DROP FUNCTION IF EXISTS reject_audit_entries_mutation();
DROP TABLE IF EXISTS audit_entries;
