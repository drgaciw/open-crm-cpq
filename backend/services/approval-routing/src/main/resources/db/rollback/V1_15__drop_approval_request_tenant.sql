-- Manual rollback for V1_15__approval_request_tenant.sql.
-- Flyway Community does not run this script. Apply it in the approval schema.
-- Do not drop account_id while approval-routing code that reads it is deployed.
--
-- tenant_id and idx_approval_tenant_status belong to V1_14 (approval matrix) and stay.
-- tenant_id is not narrowed back to VARCHAR(64): rows written at the wider width could fail it.
-- Backfilled tenant values are left in place.

DROP INDEX IF EXISTS idx_approval_tenant_quote;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS account_id;
