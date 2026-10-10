-- R3: commercial lineage, guarded ERP claims and per-line accepted rate facts, all within this service schema.
ALTER TABLE subscriptions ADD COLUMN commercial_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN erp_sync_claim_token VARCHAR(36);
ALTER TABLE subscriptions ADD COLUMN erp_sync_claim_revision BIGINT;

CREATE TABLE subscription_rate_periods (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    accepted_line_uuid VARCHAR(36) NOT NULL,
    source_line_uuid VARCHAR(36),
    accepted_quote_id VARCHAR(255),
    product_external_id VARCHAR(255),
    product_name VARCHAR(255),
    product_type VARCHAR(50),
    line_item_type VARCHAR(50),
    billing_frequency VARCHAR(30),
    sequence INTEGER NOT NULL CHECK (sequence >= 0),
    start_month INTEGER CHECK (start_month >= 1),
    months INTEGER CHECK (months >= 1),
    start_date DATE NOT NULL,
    billing_anchor_date DATE NOT NULL,
    end_date DATE NOT NULL,
    effective_end_date DATE NOT NULL,
    quantity DECIMAL(19,4) CHECK (quantity >= 0),
    net_unit_price DECIMAL(19,4) CHECK (net_unit_price >= 0),
    net_monthly DECIMAL(19,2) CHECK (net_monthly >= 0),
    annual_run_rate DECIMAL(19,2),
    period_amount DECIMAL(19,2),
    gross_period_amount DECIMAL(19,2),
    migration_credit DECIMAL(19,2),
    discount_rate DECIMAL(19,8),
    explanation_codes JSONB,
    currency_code VARCHAR(3),
    provenance VARCHAR(30) NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    retired BOOLEAN NOT NULL DEFAULT FALSE,
    billing_period_start DATE,
    billing_period_end DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_subscription_line_period UNIQUE(subscription_id, accepted_line_uuid, sequence),
    CONSTRAINT chk_rate_dates CHECK (end_date >= start_date AND effective_end_date <= end_date)
);
CREATE INDEX idx_rate_due ON subscription_rate_periods(subscription_id, applied, start_date)
    WHERE NOT is_deleted AND NOT retired;

CREATE TABLE applied_quote_acceptances (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    accepted_quote_id VARCHAR(255) NOT NULL UNIQUE,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    operation VARCHAR(20) NOT NULL,
    commercial_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Keep historical MRR/ARR/date/currency facts without inventing unit price, quantity or historical TCV.
-- The reserved non-UUID legacy identity cannot be a newly validated accepted-line UUID.
-- Backfilled periods never emit retroactive RampApplied.
INSERT INTO subscription_rate_periods (
    uuid, subscription_id, accepted_line_uuid, accepted_quote_id, product_name, sequence,
    start_date, billing_anchor_date, end_date, effective_end_date, net_monthly, annual_run_rate,
    currency_code, provenance, applied, created_by
)
SELECT (substr(md5('rate-backfill:' || uuid),1,8) || '-' || substr(md5('rate-backfill:' || uuid),9,4) || '-'
        || substr(md5('rate-backfill:' || uuid),13,4) || '-' || substr(md5('rate-backfill:' || uuid),17,4) || '-'
        || substr(md5('rate-backfill:' || uuid),21,12)),
       id, 'legacy-aggregate', quote_id, plan_name, 1, start_date, start_date, end_date, end_date,
       COALESCE(mrr, ROUND(arr / 12, 2)), arr,
       currency_code, 'LEGACY_AGGREGATE', TRUE, 'r3-rate-backfill'
FROM subscriptions WHERE NOT is_deleted;
