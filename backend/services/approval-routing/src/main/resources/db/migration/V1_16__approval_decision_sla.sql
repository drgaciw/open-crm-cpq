-- DRG-444 / BC-04-04. Decision comment and the SLA escalation path.
-- Escalation keeps status PENDING and advances approval_tier. A null sla_deadline is not due.
-- tenant_id is owned by the H4 tenant-isolation change, not this migration.
-- Rollback: db/rollback/V1_16__drop_approval_decision_sla.sql

ALTER TABLE approval_requests ADD COLUMN decision_comment TEXT;
ALTER TABLE approval_requests ADD COLUMN sla_deadline TIMESTAMPTZ;
ALTER TABLE approval_requests ADD COLUMN escalation_path TEXT;
ALTER TABLE approval_requests ADD COLUMN escalated_from_tier VARCHAR(50);
ALTER TABLE approval_requests ADD COLUMN escalated_at TIMESTAMPTZ;

CREATE INDEX idx_approval_sla_due ON approval_requests (sla_deadline)
    WHERE status = 'PENDING' AND is_deleted = FALSE AND sla_deadline IS NOT NULL;
