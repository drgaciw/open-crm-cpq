-- subscriptions.tenant_id (DRG-662 / S-T1).
-- Set once at creation from QuoteAccepted top-level tenantId (DRG-631 provenance).
-- Interim Entra tid (per-deployment, ADR-016 / DRG-632); not a verified customer tenant.
-- Null when absent/non-GUID or for service-token accepts. No backfill (S7-class).
-- Must equal contracts.tenant_id when contract_id is set (apply fails closed otherwise).
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the contract schema:
--   ALTER TABLE subscriptions DROP COLUMN IF EXISTS tenant_id;
--
-- Dropping is safe only after code that reads subscriptions.tenant_id is undeployed.

ALTER TABLE subscriptions
    ADD COLUMN tenant_id uuid;

COMMENT ON COLUMN subscriptions.tenant_id IS
    'Interim Entra tid (UUID) copied from QuoteAccepted.tenantId at creation. Null when absent/non-GUID. Immutable. Must equal contracts.tenant_id when contract_id is set.';
