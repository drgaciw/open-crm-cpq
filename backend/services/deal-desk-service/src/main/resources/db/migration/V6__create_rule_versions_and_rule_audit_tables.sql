CREATE TABLE deal_desk.rule_versions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    deal_rule_id BIGINT NOT NULL REFERENCES deal_desk.deal_rules(id),
    rule_version_number INTEGER NOT NULL,
    condition_expression TEXT NOT NULL,
    action_expression TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    draft BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    checksum VARCHAR(128),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_rule_versions_rule_version UNIQUE (deal_rule_id, rule_version_number)
);

CREATE INDEX idx_rule_versions_rule ON deal_desk.rule_versions(deal_rule_id);
CREATE INDEX idx_rule_versions_active ON deal_desk.rule_versions(active);

CREATE TABLE deal_desk.rule_audit (
    id BIGSERIAL PRIMARY KEY,
    deal_rule_id BIGINT NOT NULL REFERENCES deal_desk.deal_rules(id),
    rule_version_id BIGINT REFERENCES deal_desk.rule_versions(id),
    event_type VARCHAR(100) NOT NULL,
    actor VARCHAR(255),
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rule_audit_rule ON deal_desk.rule_audit(deal_rule_id);
CREATE INDEX idx_rule_audit_version ON deal_desk.rule_audit(rule_version_id);
CREATE INDEX idx_rule_audit_event_type ON deal_desk.rule_audit(event_type);
