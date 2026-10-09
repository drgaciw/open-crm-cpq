-- Additive snapshot binding. Legacy flat approvals retain null fields.
ALTER TABLE approval_requests ADD COLUMN pricing_fingerprint VARCHAR(64);
ALTER TABLE approval_requests ADD COLUMN governing_period INTEGER;
ALTER TABLE approval_requests ADD CONSTRAINT chk_ramp_approval_binding CHECK (
    (pricing_fingerprint IS NULL AND governing_period IS NULL)
    OR (pricing_fingerprint IS NOT NULL AND governing_period IS NOT NULL
        AND pricing_fingerprint ~ '^[a-f0-9]{64}$' AND governing_period > 0)
);
