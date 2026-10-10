-- Additive identities and validated authoring context; existing quotes remain readable.
ALTER TABLE quotes ADD COLUMN target_subscription_number VARCHAR(255);
ALTER TABLE quotes ADD COLUMN observed_revision BIGINT;
ALTER TABLE quotes ADD COLUMN subscription_effective_date DATE;
ALTER TABLE quotes ADD COLUMN continuation_request_json TEXT;
ALTER TABLE quote_line_items ADD COLUMN source_subscription_line_uuid VARCHAR(255);
ALTER TABLE quote_line_items ADD COLUMN hybrid_migration_json TEXT;
ALTER TABLE quote_line_items ADD COLUMN initial_proration_json TEXT;
