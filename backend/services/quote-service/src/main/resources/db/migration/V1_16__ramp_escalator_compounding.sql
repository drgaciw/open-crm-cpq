-- Authoring metadata only. NULL retains legacy compound growth; no backfill required.
-- Rollback: ALTER TABLE quote.quote_line_items DROP COLUMN ramp_escalator_compounding;
ALTER TABLE quote.quote_line_items ADD COLUMN ramp_escalator_compounding BOOLEAN;
