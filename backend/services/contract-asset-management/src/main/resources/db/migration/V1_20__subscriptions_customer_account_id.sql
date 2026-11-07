-- DRG-663 (S-T2). Additive nullable subscriptions.customer_account_id.
-- Stays NULL at QuoteAccepted ingest until a uuid account source exists.
-- Never parse customer_id varchar into this column at ingest (external String id).
-- When contract_id is set, typed key must equal contracts.customer_account_id or
-- apply fails closed (CUSTOMER_ACCOUNT_UNSET / CUSTOMER_ACCOUNT_MISMATCH).
-- Backfill is S7-class and NO-GO.
-- Slot: main tip was V1_15. Sibling drafts reserved V1_16–V1_19; this story uses V1_20.
-- Rollback: db/rollback/V1_20__drop_subscriptions_customer_account_id.sql

ALTER TABLE subscriptions
    ADD COLUMN customer_account_id uuid;

CREATE INDEX idx_subscriptions_customer_account_id ON subscriptions (customer_account_id);

COMMENT ON COLUMN subscriptions.customer_account_id IS
    'Typed CRM account uuid. NULL until a uuid account source exists. Must equal contracts.customer_account_id when contract_id is set. Legacy customer_id varchar remains.';
