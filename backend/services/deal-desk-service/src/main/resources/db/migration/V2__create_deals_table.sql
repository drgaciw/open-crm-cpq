CREATE TABLE deal_desk.deals (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    deal_value NUMERIC(19,4),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    salesforce_opportunity_id VARCHAR(255) UNIQUE,
    account_id VARCHAR(255),
    account_name VARCHAR(255),
    notes TEXT,
    synced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_deal_status ON deal_desk.deals(status);
CREATE INDEX idx_deal_account_id ON deal_desk.deals(account_id);
CREATE INDEX idx_deal_sf_opp_id ON deal_desk.deals(salesforce_opportunity_id);
