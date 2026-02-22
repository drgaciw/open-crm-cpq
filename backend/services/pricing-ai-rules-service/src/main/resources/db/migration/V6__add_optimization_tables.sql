CREATE TABLE IF NOT EXISTS pricing_ai_rules.rule_optimization (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT,
    rule_set_id BIGINT,
    original_content TEXT NOT NULL,
    optimized_content TEXT NOT NULL,
    optimization_type VARCHAR(100) NOT NULL,
    performance_improvement_pct DECIMAL(5, 2),
    applied BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_pricing_ai_rules_optimization_rule
        FOREIGN KEY (rule_id) REFERENCES pricing_ai_rules.rule (id),
    CONSTRAINT fk_pricing_ai_rules_optimization_rule_set
        FOREIGN KEY (rule_set_id) REFERENCES pricing_ai_rules.rule_set (id)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.optimization_metrics (
    id BIGINT PRIMARY KEY,
    optimization_id BIGINT NOT NULL,
    original_execution_time_ms BIGINT,
    optimized_execution_time_ms BIGINT,
    improvement_pct DECIMAL(5, 2),
    tested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_optimization_metrics
        FOREIGN KEY (optimization_id) REFERENCES pricing_ai_rules.rule_optimization (id)
);

CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_optimization_rule_id
    ON pricing_ai_rules.rule_optimization (rule_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_optimization_rule_set_id
    ON pricing_ai_rules.rule_optimization (rule_set_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_optimization_type
    ON pricing_ai_rules.rule_optimization (optimization_type);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_optimization_applied
    ON pricing_ai_rules.rule_optimization (applied);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_optimization_metrics_opt_id
    ON pricing_ai_rules.optimization_metrics (optimization_id);
