SET search_path TO quote;

ALTER TABLE quotes ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

-- NULLS NOT DISTINCT also protects legacy unowned rows. The API requires a tenant.
CREATE UNIQUE INDEX uq_quotes_primary_opportunity
    ON quotes (tenant_id, deal_external_id) NULLS NOT DISTINCT
    WHERE is_primary = TRUE AND deal_external_id IS NOT NULL AND is_deleted = FALSE;

-- Manual rollback after reverting application code:
-- DROP INDEX quote.uq_quotes_primary_opportunity;
-- ALTER TABLE quote.quotes DROP COLUMN is_primary;
