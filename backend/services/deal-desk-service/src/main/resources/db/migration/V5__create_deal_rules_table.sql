CREATE TABLE deal_desk.deal_rules (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    rule_key VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL DEFAULT 'general',
    priority INTEGER NOT NULL DEFAULT 100,
    condition_expression TEXT NOT NULL,
    action_expression TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    current_version_number INTEGER,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_deal_rules_category ON deal_desk.deal_rules(category);
CREATE INDEX idx_deal_rules_active ON deal_desk.deal_rules(active);
CREATE INDEX idx_deal_rules_rule_key ON deal_desk.deal_rules(rule_key);
