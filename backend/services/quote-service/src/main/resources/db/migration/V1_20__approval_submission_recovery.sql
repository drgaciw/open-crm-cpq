ALTER TABLE quotes ADD COLUMN approval_submission_id BIGINT;
ALTER TABLE quotes ADD COLUMN approval_submission_version BIGINT;
CREATE INDEX idx_quotes_approval_recovery ON quotes (id)
    WHERE is_deleted = FALSE AND status = 'PENDING_APPROVAL' AND approval_submission_id IS NOT NULL;
