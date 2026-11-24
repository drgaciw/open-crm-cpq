-- Security H4. This migration owns approval_requests.tenant_id.
-- DRG-444 rebases onto this column and must not add tenant_id again.
-- Width is 128, the same as quote.quotes.tenant_id, so the backfill cannot overflow.
-- tenant_id is the Entra tid of the authenticated principal at create time.
-- It is never taken from a request header or body.
-- A caller whose tid differs, or a row whose tenant could not be backfilled, is 403 CROSS_TENANT.
-- The row is not returned.
-- account_id is the principal account_id claim when the token carries one.
-- Rollback: db/rollback/V1_12__drop_approval_request_tenant.sql
--
-- Backfill: when this database also has quote.quotes, copy that quote's tenant_id and,
-- when the approval has no account yet, its customer_external_id. quote_id is VARCHAR, so
-- only a numeric quote_id can match quotes.id. A quote with a null tenant, a deleted quote,
-- a missing quote, or a non-numeric quote id (Kafka discount approvals) is not derivable:
-- those rows stay null. A caller with a tenant who opens a null or different tenant row
-- gets 403 CROSS_TENANT and not the row. A deployment with no quote schema
-- backfills nothing; the same null rule applies.
-- An approval whose tenant_id is already set is left unchanged.

ALTER TABLE approval_requests ADD COLUMN tenant_id VARCHAR(128);
ALTER TABLE approval_requests ADD COLUMN account_id VARCHAR(255);

CREATE INDEX idx_approval_tenant_status ON approval_requests (tenant_id, status);
CREATE INDEX idx_approval_tenant_quote ON approval_requests (tenant_id, quote_id);

COMMENT ON COLUMN approval_requests.tenant_id IS
    'Entra tid of the principal that created the request. Null only when it could not be backfilled from quote.quotes, or for a dev-mode write with no verified token. A caller with a tenant never receives a null or different tenant.';

COMMENT ON COLUMN approval_requests.account_id IS
    'Account the approval belongs to when known: principal account_id claim, or quote.quotes.customer_external_id after backfill. Null when the account is not known. An account-scoped caller does not see a different account.';

DO $$
BEGIN
    IF to_regclass('quote.quotes') IS NOT NULL THEN
        UPDATE approval_requests AS ar
        SET tenant_id = q.tenant_id,
            account_id = COALESCE(ar.account_id, q.customer_external_id)
        FROM quote.quotes AS q
        WHERE ar.tenant_id IS NULL
          AND q.is_deleted = FALSE
          AND ar.quote_id ~ '^[0-9]+$'
          AND q.id = ar.quote_id::bigint
          AND (q.tenant_id IS NOT NULL OR q.customer_external_id IS NOT NULL);
    END IF;
END $$;
