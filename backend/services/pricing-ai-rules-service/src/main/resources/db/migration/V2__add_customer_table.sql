CREATE TABLE IF NOT EXISTS pricing_ai_rules.customer (
    id BIGINT PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    segment VARCHAR(50),
    region VARCHAR(50),
    industry VARCHAR(100),
    churn_score DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_customer_external_id
    ON pricing_ai_rules.customer (external_id);
