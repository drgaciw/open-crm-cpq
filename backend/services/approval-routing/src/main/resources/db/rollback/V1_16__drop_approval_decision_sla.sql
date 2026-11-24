-- Guarded rollback for V1_16. Not a Flyway migration.
-- Drops the DRG-444 decision comment and SLA columns. Does not touch tenant_id.

DROP INDEX IF EXISTS idx_approval_sla_due;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS decision_comment;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS sla_deadline;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS escalation_path;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS escalated_from_tier;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS escalated_at;

DELETE FROM flyway_schema_history WHERE version = '1.12';
