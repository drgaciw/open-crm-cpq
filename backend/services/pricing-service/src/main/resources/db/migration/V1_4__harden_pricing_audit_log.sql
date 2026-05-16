ALTER TABLE pricing_audit_log
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pricebook_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ruleset_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS metrics_snapshot TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_correlation ON pricing_audit_log(correlation_id);
