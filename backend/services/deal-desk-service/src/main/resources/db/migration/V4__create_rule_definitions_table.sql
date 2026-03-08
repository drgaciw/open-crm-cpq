CREATE TABLE deal_desk.rule_definitions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    rule_key VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    description TEXT,
    category VARCHAR(100),
    priority INTEGER,
    condition_expression TEXT,
    action_expression TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    parameters_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_rule_definitions_category ON deal_desk.rule_definitions(category);
CREATE INDEX idx_rule_definitions_enabled ON deal_desk.rule_definitions(enabled);
