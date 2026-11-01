-- DRG-656. Read model for asset history, the supersede chain, and as-of installed base.
-- Version taken as the next free CAM migration at build time (after V1_13).
-- CREATED rows come from AssetCreated. Amended, superseded, and terminated rows are accepted
-- shapes only. Their producers are later stories, so this migration registers no event contract.

CREATE TABLE asset_history (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    effective_end DATE,
    source_amendment_id VARCHAR(36),
    source_apply_unit VARCHAR(64),
    predecessor_asset_id VARCHAR(36),
    successor_asset_id VARCHAR(36),
    contract_line_id VARCHAR(64),
    subscription_id BIGINT,
    product_ref VARCHAR(255),
    quantity DECIMAL(19, 4),
    applied_order BIGINT NOT NULL,
    CONSTRAINT uq_asset_history_event UNIQUE (event_id),
    CONSTRAINT chk_asset_history_type CHECK (event_type IN ('CREATED', 'AMENDED', 'SUPERSEDED', 'TERMINATED'))
);

CREATE INDEX idx_asset_history_asset ON asset_history (asset_id, effective_date, applied_order);
CREATE INDEX idx_asset_history_account ON asset_history (account_id, effective_date, applied_order);
CREATE INDEX idx_asset_history_type ON asset_history (event_type);
CREATE INDEX idx_asset_history_predecessor ON asset_history (predecessor_asset_id);
CREATE INDEX idx_asset_history_successor ON asset_history (successor_asset_id);
