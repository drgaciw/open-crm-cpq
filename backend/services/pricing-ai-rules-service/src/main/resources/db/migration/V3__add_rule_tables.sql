CREATE TABLE IF NOT EXISTS pricing_ai_rules.rule_set (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    effective_date TIMESTAMP WITH TIME ZONE,
    expiration_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.rule (
    id BIGINT PRIMARY KEY,
    rule_set_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    priority INTEGER DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_pricing_ai_rules_rule_rule_set
        FOREIGN KEY (rule_set_id) REFERENCES pricing_ai_rules.rule_set (id)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.rule_dependency (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    dependent_rule_id BIGINT NOT NULL,
    dependency_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_dependency_rule
        FOREIGN KEY (rule_id) REFERENCES pricing_ai_rules.rule (id),
    CONSTRAINT fk_pricing_ai_rules_dependency_dependent_rule
        FOREIGN KEY (dependent_rule_id) REFERENCES pricing_ai_rules.rule (id)
);

CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_rule_set_name
    ON pricing_ai_rules.rule_set (name);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_rule_set_status
    ON pricing_ai_rules.rule_set (status);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_rule_name
    ON pricing_ai_rules.rule (name);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_rule_rule_set_id
    ON pricing_ai_rules.rule (rule_set_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_rule_status
    ON pricing_ai_rules.rule (status);
