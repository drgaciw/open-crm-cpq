-- DRG-654. One asset per activated contract line. Version taken as the next free CAM migration at build time.
-- account_id stores QuoteAcceptedEvent.customerId. There is no customer_id column.
-- entitlement_id stays null until an entitlement exists to link (DRG-700).

CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    account_id VARCHAR(255) NOT NULL,
    contract_line_id VARCHAR(64) NOT NULL,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions (id),
    entitlement_id VARCHAR(64),
    product_ref VARCHAR(255),
    quantity DECIMAL(19, 4),
    status VARCHAR(20) NOT NULL,
    effective_start DATE NOT NULL,
    effective_end DATE NOT NULL,
    superseded_by VARCHAR(36),
    supersedes VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_asset_subscription_contract_line UNIQUE (subscription_id, contract_line_id),
    CONSTRAINT chk_asset_status CHECK (status IN ('ACTIVE', 'AMENDED', 'SUPERSEDED', 'TERMINATED'))
);

CREATE INDEX idx_asset_account ON assets (account_id);
CREATE INDEX idx_asset_contract_line ON assets (contract_line_id);
CREATE INDEX idx_asset_subscription ON assets (subscription_id);
