-- Quote.tenant_id. Set on create and clone from the caller's Entra tid. No backfill.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the quote schema:
--   ALTER TABLE quotes DROP COLUMN IF EXISTS tenant_id;
--
-- V1_13 adds a nullable column with no default and no backfill. Existing rows stay NULL.
-- Dropping it is safe only if code that reads tenant_id is not deployed. quote-service
-- reads the column when it sets tenant on create and clone.
-- Do not drop the column while that code is deployed.

SET search_path TO quote;

ALTER TABLE quotes ADD COLUMN tenant_id VARCHAR(128);

COMMENT ON COLUMN quotes.tenant_id IS
    'Entra tid of the user who created or cloned the quote. Null for service tokens, anonymous callers, dev mode, and rows from before this column.';
