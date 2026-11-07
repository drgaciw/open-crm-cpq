-- DRG-666 (S-TX1). Additive subscriptions.subscription_model (ADR §2.2) plus ingest rejection audit.
-- Slot coordination: main tip was V1_15. Sibling drafts in flight claimed:
--   DRG-662 → V1_16 (tenant_id); DRG-665 → V1_16+V1_17 (contract_lines; collides with 662 on V1_16).
-- This story uses V1_19 so both siblings can rebase into V1_16..V1_18 without colliding here.
-- Not reserved. Does NOT register SubscriptionActivated / EntitlementProvisioned.
-- Legacy rows stay NULL; dry-run classification only. Backfill is S7-class and NO-GO.
-- Rollback: db/rollback/V1_19__drop_subscription_model.sql

ALTER TABLE subscriptions
    ADD COLUMN subscription_model VARCHAR(32);

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscription_model CHECK (
        subscription_model IS NULL
            OR subscription_model IN ('SAAS_RECURRING', 'ONPREM_TRANSACTIONAL', 'ONPREM_CAPACITY')
    );

CREATE TABLE subscription_ingest_audits (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    accepted_quote_id VARCHAR(255) NOT NULL,
    audit_code VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    product_type VARCHAR(50),
    detail VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_ingest_audit_code CHECK (audit_code = 'SUBSCRIPTION_INGEST_REJECTED'),
    CONSTRAINT chk_subscription_ingest_reason_code CHECK (reason_code = 'UNKNOWN_SUBSCRIPTION_MODEL')
);

CREATE INDEX idx_subscription_ingest_audits_quote
    ON subscription_ingest_audits (accepted_quote_id);
