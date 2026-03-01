-- =====================================================
-- Flyway Migration V1: Create Configuration Schema
-- =====================================================
-- Description: Initial schema for product configuration management
-- Author: System
-- Date: 2026-03-01
-- =====================================================

SET search_path TO configuration;

-- =====================================================
-- TABLE: product_configurations
-- Purpose: Aggregate root for configurable products
-- =====================================================
CREATE TABLE product_configurations (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    product_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    max_options INT,
    min_options INT,
    allow_duplicates BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_config_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT chk_config_options CHECK (max_options IS NULL OR min_options IS NULL OR max_options >= min_options)
);

CREATE INDEX idx_config_product_id ON product_configurations(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_config_code ON product_configurations(code) WHERE NOT is_deleted;
CREATE INDEX idx_config_status ON product_configurations(status) WHERE NOT is_deleted;

COMMENT ON TABLE product_configurations IS 'Aggregate root for product configuration definitions';
COMMENT ON COLUMN product_configurations.product_id IS 'Reference to the product in the Product Catalog service';

-- =====================================================
-- TABLE: configuration_options
-- Purpose: Selectable options within a configuration
-- =====================================================
CREATE TABLE configuration_options (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    configuration_id BIGINT NOT NULL REFERENCES product_configurations(id),
    option_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    referenced_product_id BIGINT,
    price_adjustment DECIMAL(19,2) DEFAULT 0,
    adjustment_type VARCHAR(20) DEFAULT 'ABSOLUTE',
    min_quantity INT DEFAULT 0,
    max_quantity INT DEFAULT 1,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    group_name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_opt_adjustment_type CHECK (adjustment_type IN ('ABSOLUTE', 'PERCENTAGE')),
    CONSTRAINT chk_opt_quantity CHECK (max_quantity >= min_quantity)
);

CREATE INDEX idx_cfg_opt_config_id ON configuration_options(configuration_id) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_opt_option_code ON configuration_options(option_code) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_opt_product_id ON configuration_options(referenced_product_id) WHERE NOT is_deleted;

COMMENT ON TABLE configuration_options IS 'Selectable options within a product configuration';
COMMENT ON COLUMN configuration_options.referenced_product_id IS 'Optional reference to a product this option represents';

-- =====================================================
-- TABLE: config_rules
-- Purpose: Rules governing option relationships
-- =====================================================
CREATE TABLE config_rules (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    configuration_id BIGINT NOT NULL REFERENCES product_configurations(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(30) NOT NULL,
    source_option_code VARCHAR(100) NOT NULL,
    target_option_code VARCHAR(100),
    rule_action VARCHAR(30) NOT NULL DEFAULT 'BLOCK',
    condition_expression TEXT,
    error_message VARCHAR(500),
    min_quantity INT,
    max_quantity INT,
    priority INT NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_rule_type CHECK (rule_type IN ('COMPATIBILITY', 'DEPENDENCY', 'EXCLUSION', 'QUANTITY_VALIDATION')),
    CONSTRAINT chk_rule_action CHECK (rule_action IN ('AUTO_ADD', 'AUTO_REMOVE', 'WARN', 'BLOCK', 'ENFORCE_QUANTITY'))
);

CREATE INDEX idx_cfg_rule_config_id ON config_rules(configuration_id) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_rule_source ON config_rules(source_option_code) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_rule_target ON config_rules(target_option_code) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_rule_type ON config_rules(rule_type) WHERE NOT is_deleted;

COMMENT ON TABLE config_rules IS 'Rules governing relationships between configuration options';

-- =====================================================
-- TABLE: config_product_bundles
-- Purpose: Product bundles with pricing overrides
-- =====================================================
CREATE TABLE config_product_bundles (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    bundle_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    discount_type VARCHAR(20) DEFAULT 'NONE',
    discount_value DECIMAL(19,4),
    fixed_price DECIMAL(19,2),
    currency VARCHAR(3) DEFAULT 'USD',
    min_quantity INT DEFAULT 1,
    max_quantity INT,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_bundle_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT chk_bundle_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'NONE')),
    CONSTRAINT chk_bundle_validity CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_cfg_bundle_code ON config_product_bundles(bundle_code) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_bundle_status ON config_product_bundles(status) WHERE NOT is_deleted;

COMMENT ON TABLE config_product_bundles IS 'Product bundles with pricing overrides and quantity restrictions';

-- =====================================================
-- TABLE: config_bundle_line_items
-- Purpose: Individual items within bundles
-- =====================================================
CREATE TABLE config_bundle_line_items (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    bundle_id BIGINT NOT NULL REFERENCES config_product_bundles(id),
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255),
    quantity INT NOT NULL DEFAULT 1,
    min_quantity INT DEFAULT 1,
    max_quantity INT,
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    price_override DECIMAL(19,2),
    discount_percentage DECIMAL(5,2),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_bli_quantity CHECK (quantity > 0),
    CONSTRAINT chk_bli_discount CHECK (discount_percentage IS NULL OR (discount_percentage >= 0 AND discount_percentage <= 100))
);

CREATE INDEX idx_cfg_bli_bundle_id ON config_bundle_line_items(bundle_id) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_bli_product_id ON config_bundle_line_items(product_id) WHERE NOT is_deleted;

COMMENT ON TABLE config_bundle_line_items IS 'Line items within product bundles with pricing overrides';

-- =====================================================
-- TABLE: configuration_templates
-- Purpose: Reusable configuration templates
-- =====================================================
CREATE TABLE configuration_templates (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    template_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    template_data TEXT NOT NULL,
    source_configuration_id BIGINT REFERENCES product_configurations(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_tmpl_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED'))
);

CREATE INDEX idx_cfg_tmpl_code ON configuration_templates(template_code) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_tmpl_status ON configuration_templates(status) WHERE NOT is_deleted;
CREATE INDEX idx_cfg_tmpl_category ON configuration_templates(category) WHERE NOT is_deleted;

COMMENT ON TABLE configuration_templates IS 'Reusable configuration templates for quick product setup';

-- =====================================================
-- TABLE: outbox_events
-- Purpose: Transactional outbox for reliable Kafka publishing
-- =====================================================
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_processed ON outbox_events(processed) WHERE NOT processed;
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);

COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable Kafka event publishing';
