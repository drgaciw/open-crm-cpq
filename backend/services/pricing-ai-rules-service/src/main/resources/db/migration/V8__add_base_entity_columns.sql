-- Add BaseEntity standard columns (uuid, created_by, updated_by, version, is_deleted)
-- to all pricing_ai_rules tables that now extend BaseEntity.

ALTER TABLE pricing_ai_rules.customer
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.quote
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.product
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.product_bundle
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.product_bundle_item
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.quote_line_item
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.rule_set
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.rule
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.rule_dependency
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.financial_metrics_log
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.optimization_metrics
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.rule_execution_metrics
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE pricing_ai_rules.rule_optimization
    ADD COLUMN IF NOT EXISTS uuid VARCHAR(36) NOT NULL DEFAULT gen_random_uuid()::text,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version BIGINT,
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

-- Add unique constraints on uuid for tables that didn't have them
ALTER TABLE pricing_ai_rules.customer ADD CONSTRAINT uk_customer_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.quote ADD CONSTRAINT uk_quote_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.product ADD CONSTRAINT uk_product_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.product_bundle ADD CONSTRAINT uk_product_bundle_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.product_bundle_item ADD CONSTRAINT uk_product_bundle_item_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.quote_line_item ADD CONSTRAINT uk_quote_line_item_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.rule_set ADD CONSTRAINT uk_rule_set_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.rule ADD CONSTRAINT uk_rule_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.rule_dependency ADD CONSTRAINT uk_rule_dependency_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.financial_metrics_log ADD CONSTRAINT uk_financial_metrics_log_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.optimization_metrics ADD CONSTRAINT uk_optimization_metrics_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.rule_execution_metrics ADD CONSTRAINT uk_rule_execution_metrics_uuid UNIQUE (uuid);
ALTER TABLE pricing_ai_rules.rule_optimization ADD CONSTRAINT uk_rule_optimization_uuid UNIQUE (uuid);
