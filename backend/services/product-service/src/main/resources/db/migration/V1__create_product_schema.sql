-- =====================================================
-- Flyway Migration V1: Create Product Schema
-- =====================================================
-- Description: Initial schema creation for product catalog
-- Author: System
-- Date: 2026-02-07
-- =====================================================

SET search_path TO product;

-- =====================================================
-- TABLE: products
-- Purpose: Full product catalog with all product types
-- =====================================================
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    product_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    list_price DECIMAL(19,2),
    currency VARCHAR(3) DEFAULT 'USD',
    billing_frequency VARCHAR(20),
    unit_of_measure VARCHAR(50),
    is_configurable BOOLEAN NOT NULL DEFAULT FALSE,
    is_bundle BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_products_product_type CHECK (product_type IN (
        'SAAS_SEAT_NAMED', 'SAAS_SEAT_CONCURRENT', 'SAAS_USAGE_METERED', 'SAAS_FLAT_RATE',
        'ONPREM_PERPETUAL', 'ONPREM_CORE', 'ONPREM_SUBSCRIPTION', 'HYBRID_PORTABLE',
        'SUPPORT_BASIC', 'SUPPORT_PREMIUM', 'SUPPORT_ENTERPRISE',
        'PROFESSIONAL_SERVICES', 'TRAINING',
        'ADD_ON_API', 'ADD_ON_STORAGE', 'ADD_ON_ANALYTICS', 'ADD_ON_SECURITY', 'ADD_ON_INTEGRATION',
        'HARDWARE', 'CUSTOM'
    )),
    CONSTRAINT chk_products_category CHECK (category IN (
        'SUBSCRIPTION', 'LICENSE', 'SUPPORT', 'SERVICES', 'ADD_ON'
    )),
    CONSTRAINT chk_products_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'
    )),
    CONSTRAINT chk_products_billing_frequency CHECK (billing_frequency IS NULL OR billing_frequency IN (
        'MONTHLY', 'QUARTERLY', 'ANNUAL', 'ONE_TIME'
    ))
);

CREATE INDEX idx_products_sku ON products(sku) WHERE NOT is_deleted;
CREATE INDEX idx_products_product_type ON products(product_type) WHERE NOT is_deleted;
CREATE INDEX idx_products_category ON products(category) WHERE NOT is_deleted;
CREATE INDEX idx_products_status ON products(status) WHERE NOT is_deleted;
CREATE INDEX idx_products_is_bundle ON products(is_bundle) WHERE NOT is_deleted;
CREATE INDEX idx_products_is_configurable ON products(is_configurable) WHERE NOT is_deleted;
CREATE INDEX idx_products_created_at ON products(created_at);

COMMENT ON TABLE products IS 'Main product catalog containing all product types and variations';
COMMENT ON COLUMN products.product_type IS 'Specific product type determining licensing and billing model';
COMMENT ON COLUMN products.category IS 'High-level product category for classification';
COMMENT ON COLUMN products.metadata IS 'JSON blob for flexible product-specific attributes';

-- =====================================================
-- TABLE: product_bundles
-- Purpose: Bundle definitions for product packages
-- =====================================================
CREATE TABLE product_bundles (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20),
    discount_value DECIMAL(19,4),
    min_items INT,
    max_items INT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_product_bundles_discount_type CHECK (discount_type IS NULL OR discount_type IN (
        'PERCENTAGE', 'FIXED_AMOUNT', 'NONE'
    )),
    CONSTRAINT chk_product_bundles_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'
    )),
    CONSTRAINT chk_product_bundles_items CHECK (max_items IS NULL OR min_items IS NULL OR max_items >= min_items)
);

CREATE INDEX idx_product_bundles_product_id ON product_bundles(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_bundles_status ON product_bundles(status) WHERE NOT is_deleted;

COMMENT ON TABLE product_bundles IS 'Bundle definitions linking parent products to child items';
COMMENT ON COLUMN product_bundles.product_id IS 'The parent product that represents this bundle';

-- =====================================================
-- TABLE: product_bundle_items
-- Purpose: Individual items within product bundles
-- =====================================================
CREATE TABLE product_bundle_items (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    bundle_id BIGINT NOT NULL REFERENCES product_bundles(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL DEFAULT 1,
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_product_bundle_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_product_bundle_items_bundle_id ON product_bundle_items(bundle_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_bundle_items_product_id ON product_bundle_items(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_bundle_items_sort_order ON product_bundle_items(bundle_id, sort_order) WHERE NOT is_deleted;

COMMENT ON TABLE product_bundle_items IS 'Child products included in bundles with quantity and optionality';
COMMENT ON COLUMN product_bundle_items.product_id IS 'The child product included in the bundle';

-- =====================================================
-- TABLE: product_option_groups
-- Purpose: Groups of configurable options for products
-- =====================================================
CREATE TABLE product_option_groups (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    selection_type VARCHAR(20) NOT NULL,
    min_selections INT DEFAULT 0,
    max_selections INT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_product_option_groups_selection_type CHECK (selection_type IN (
        'SINGLE', 'MULTIPLE', 'REQUIRED_SINGLE'
    )),
    CONSTRAINT chk_product_option_groups_selections CHECK (
        max_selections IS NULL OR min_selections IS NULL OR max_selections >= min_selections
    )
);

CREATE INDEX idx_product_option_groups_product_id ON product_option_groups(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_option_groups_sort_order ON product_option_groups(product_id, sort_order) WHERE NOT is_deleted;

COMMENT ON TABLE product_option_groups IS 'Option groups for configurable products';
COMMENT ON COLUMN product_option_groups.selection_type IS 'Controls how many options can be selected from this group';

-- =====================================================
-- TABLE: product_options
-- Purpose: Individual options within option groups
-- =====================================================
CREATE TABLE product_options (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    option_group_id BIGINT NOT NULL REFERENCES product_option_groups(id),
    product_id BIGINT REFERENCES products(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_adjustment DECIMAL(19,2) DEFAULT 0,
    adjustment_type VARCHAR(20) DEFAULT 'ABSOLUTE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_product_options_adjustment_type CHECK (adjustment_type IN (
        'ABSOLUTE', 'PERCENTAGE'
    ))
);

CREATE INDEX idx_product_options_option_group_id ON product_options(option_group_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_options_product_id ON product_options(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_options_sort_order ON product_options(option_group_id, sort_order) WHERE NOT is_deleted;

COMMENT ON TABLE product_options IS 'Individual selectable options within option groups';
COMMENT ON COLUMN product_options.product_id IS 'Optional reference to a product this option adds';
COMMENT ON COLUMN product_options.price_adjustment IS 'Price modification when this option is selected';

-- =====================================================
-- TABLE: product_attributes
-- Purpose: Dynamic attribute definitions
-- =====================================================
CREATE TABLE product_attributes (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255),
    data_type VARCHAR(20) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value VARCHAR(500),
    validation_regex VARCHAR(500),
    enum_values TEXT,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_product_attributes_data_type CHECK (data_type IN (
        'STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'ENUM'
    ))
);

CREATE UNIQUE INDEX idx_product_attributes_name ON product_attributes(name) WHERE NOT is_deleted;
CREATE INDEX idx_product_attributes_data_type ON product_attributes(data_type) WHERE NOT is_deleted;
CREATE INDEX idx_product_attributes_sort_order ON product_attributes(sort_order) WHERE NOT is_deleted;

COMMENT ON TABLE product_attributes IS 'Flexible attribute definitions that can be assigned to products';
COMMENT ON COLUMN product_attributes.enum_values IS 'JSON array of allowed values for ENUM data type';

-- =====================================================
-- TABLE: product_attribute_values
-- Purpose: Attribute values assigned to products
-- =====================================================
CREATE TABLE product_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    attribute_id BIGINT NOT NULL REFERENCES product_attributes(id),
    value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_product_attribute_values_product_id ON product_attribute_values(product_id) WHERE NOT is_deleted;
CREATE INDEX idx_product_attribute_values_attribute_id ON product_attribute_values(attribute_id) WHERE NOT is_deleted;
CREATE UNIQUE INDEX idx_product_attribute_values_unique ON product_attribute_values(product_id, attribute_id) WHERE NOT is_deleted;

COMMENT ON TABLE product_attribute_values IS 'Actual attribute values assigned to specific products';
COMMENT ON COLUMN product_attribute_values.value IS 'String representation of the attribute value';

-- =====================================================
-- TABLE: configuration_rules
-- Purpose: Product configuration rules and constraints
-- =====================================================
CREATE TABLE configuration_rules (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(30) NOT NULL,
    source_product_id BIGINT NOT NULL REFERENCES products(id),
    target_product_id BIGINT REFERENCES products(id),
    condition_expression TEXT,
    error_message VARCHAR(500),
    priority INT NOT NULL DEFAULT 100,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_configuration_rules_rule_type CHECK (rule_type IN (
        'REQUIRES', 'EXCLUDES', 'RECOMMENDS', 'DEFAULT', 'VALIDATES'
    )),
    CONSTRAINT chk_configuration_rules_status CHECK (status IN (
        'DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED'
    ))
);

CREATE INDEX idx_configuration_rules_source_product_id ON configuration_rules(source_product_id) WHERE NOT is_deleted;
CREATE INDEX idx_configuration_rules_target_product_id ON configuration_rules(target_product_id) WHERE NOT is_deleted;
CREATE INDEX idx_configuration_rules_rule_type ON configuration_rules(rule_type) WHERE NOT is_deleted;
CREATE INDEX idx_configuration_rules_status ON configuration_rules(status) WHERE NOT is_deleted;
CREATE INDEX idx_configuration_rules_priority ON configuration_rules(priority) WHERE NOT is_deleted AND status = 'ACTIVE';

COMMENT ON TABLE configuration_rules IS 'Business rules for product configuration validation and dependencies';
COMMENT ON COLUMN configuration_rules.rule_type IS 'Type of rule: REQUIRES, EXCLUDES, RECOMMENDS, DEFAULT, VALIDATES';
COMMENT ON COLUMN configuration_rules.source_product_id IS 'Product that triggers this rule';
COMMENT ON COLUMN configuration_rules.target_product_id IS 'Product affected by this rule (if applicable)';
COMMENT ON COLUMN configuration_rules.condition_expression IS 'Optional expression for complex conditional logic';
COMMENT ON COLUMN configuration_rules.priority IS 'Rule evaluation priority (lower numbers evaluate first)';

-- =====================================================
-- End of Migration V1
-- =====================================================
