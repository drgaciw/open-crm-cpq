CREATE TABLE IF NOT EXISTS pricing_ai_rules.rule_execution_metrics (
    id BIGINT PRIMARY KEY,
    rule_id BIGINT,
    rule_set_id BIGINT,
    quote_id BIGINT,
    execution_time_ms BIGINT,
    success BOOLEAN,
    error_message TEXT,
    execution_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_metrics_rule
        FOREIGN KEY (rule_id) REFERENCES pricing_ai_rules.rule (id),
    CONSTRAINT fk_pricing_ai_rules_metrics_rule_set
        FOREIGN KEY (rule_set_id) REFERENCES pricing_ai_rules.rule_set (id),
    CONSTRAINT fk_pricing_ai_rules_metrics_quote
        FOREIGN KEY (quote_id) REFERENCES pricing_ai_rules.quote (id)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.financial_metrics_log (
    id BIGINT PRIMARY KEY,
    quote_id BIGINT NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(19, 4),
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_financial_log_quote
        FOREIGN KEY (quote_id) REFERENCES pricing_ai_rules.quote (id)
);

CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_metrics_rule_id
    ON pricing_ai_rules.rule_execution_metrics (rule_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_metrics_rule_set_id
    ON pricing_ai_rules.rule_execution_metrics (rule_set_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_metrics_quote_id
    ON pricing_ai_rules.rule_execution_metrics (quote_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_metrics_execution_date
    ON pricing_ai_rules.rule_execution_metrics (execution_date);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_financial_log_quote_id
    ON pricing_ai_rules.financial_metrics_log (quote_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_financial_log_metric_name
    ON pricing_ai_rules.financial_metrics_log (metric_name);
