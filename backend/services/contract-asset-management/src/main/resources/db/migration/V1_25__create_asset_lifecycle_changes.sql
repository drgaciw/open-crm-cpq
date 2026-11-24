-- DRG-902 (902-A) durable ordered asset lifecycle schedule.
-- Nothing in production enqueues rows yet. The due runner stays inert while
-- AssetLifecycleReadiness.isReady() is false.
-- Rollback: db/rollback/V1_25__drop_asset_lifecycle_changes.sql

CREATE SEQUENCE asset_lifecycle_change_seq AS BIGINT;

CREATE TABLE asset_lifecycle_changes (
    id BIGSERIAL PRIMARY KEY,
    change_uuid VARCHAR(36) NOT NULL,
    dedupe_key VARCHAR(200) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    asset_uuid VARCHAR(36),
    subscription_id BIGINT NOT NULL REFERENCES subscriptions (id),
    contract_line_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    effective_date DATE NOT NULL,
    sequence_no BIGINT NOT NULL DEFAULT nextval('asset_lifecycle_change_seq'),
    source_type VARCHAR(30) NOT NULL,
    source_amendment_id VARCHAR(36),
    source_apply_unit VARCHAR(64),
    correlation_id VARCHAR(100),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    applied_event_id VARCHAR(36),
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_asset_lifecycle_change_uuid UNIQUE (change_uuid),
    CONSTRAINT uq_asset_lifecycle_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT chk_asset_lifecycle_kind CHECK (kind IN ('CREATE', 'AMEND', 'TERMINATE', 'SUPERSEDE')),
    CONSTRAINT chk_asset_lifecycle_source CHECK (source_type IN ('ACTIVATION', 'DOCUMENT_APPLY')),
    CONSTRAINT chk_asset_lifecycle_status CHECK (status IN ('PENDING', 'APPLIED', 'SKIPPED', 'FAILED'))
);

ALTER SEQUENCE asset_lifecycle_change_seq OWNED BY asset_lifecycle_changes.sequence_no;

CREATE INDEX idx_asset_lifecycle_due
    ON asset_lifecycle_changes (effective_date, sequence_no)
    WHERE status = 'PENDING';

CREATE INDEX idx_asset_lifecycle_line
    ON asset_lifecycle_changes (subscription_id, contract_line_id, sequence_no);
