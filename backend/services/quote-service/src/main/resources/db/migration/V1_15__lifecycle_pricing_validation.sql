-- Legacy priced changes remain unverified and must be repriced before submit.
SET search_path TO quote;

ALTER TABLE lifecycle_changes ADD COLUMN pricing_validation_version INTEGER;
ALTER TABLE lifecycle_changes ADD CONSTRAINT chk_lifecycle_pricing_validation_version
    CHECK (pricing_validation_version IS NULL OR pricing_validation_version = 1);

-- Rollback only after quote-service no longer reads this evidence column:
-- ALTER TABLE lifecycle_changes DROP COLUMN pricing_validation_version;
