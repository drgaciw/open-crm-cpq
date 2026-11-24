-- Manual rollback for V1_12__approval_request_tenant.sql.
-- Flyway Community does not run this script. Apply it in the approval schema.
-- Do not drop the columns while approval-routing code that reads tenant_id is deployed.

DROP INDEX IF EXISTS idx_approval_tenant_quote;
DROP INDEX IF EXISTS idx_approval_tenant_status;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS account_id;
ALTER TABLE approval_requests DROP COLUMN IF EXISTS tenant_id;
