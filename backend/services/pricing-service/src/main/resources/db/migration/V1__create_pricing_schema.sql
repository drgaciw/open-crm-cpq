SET search_path TO pricing;

-- Customers (pricing-specific external reference)
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) DEFAULT 'DIRECT',
    segment VARCHAR(30),
    region VARCHAR(30),
    industry VARCHAR(255),
    churn_score INTEGER,
    annual_revenue DECIMAL(19,2),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Pricing products (lightweight product reference)
CREATE TABLE pricing_products (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    list_price DECIMAL(19,4),
    currency VARCHAR(3) DEFAULT 'USD',
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Rule sets
CREATE TABLE rule_sets (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    rule_set_version INTEGER NOT NULL DEFAULT 1,
    effective_date DATE,
    expiration_date DATE,
    engine_type VARCHAR(30) NOT NULL DEFAULT 'DROOLS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Rules
CREATE TABLE rules (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    rule_set_id BIGINT NOT NULL REFERENCES rule_sets(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(30) NOT NULL,
    priority INTEGER NOT NULL CHECK (priority >= 100 AND priority <= 1000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    content TEXT NOT NULL,
    condition_group TEXT,
    action_set TEXT,
    conflict_policy VARCHAR(50) DEFAULT 'HIGHEST_PRIORITY_THEN_IMPACT',
    explanation_code VARCHAR(100),
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Price books
CREATE TABLE price_books (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(30),
    currency VARCHAR(3) DEFAULT 'USD',
    is_default BOOLEAN DEFAULT false,
    effective_date DATE,
    expiration_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Price book entries
CREATE TABLE price_book_entries (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    price_book_id BIGINT NOT NULL REFERENCES price_books(id),
    product_external_id VARCHAR(255) NOT NULL,
    list_price DECIMAL(19,4) NOT NULL,
    floor_price DECIMAL(19,4),
    currency VARCHAR(3) DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- Pricing audit log (immutable)
CREATE TABLE pricing_audit_log (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_external_id VARCHAR(255),
    customer_external_id VARCHAR(255),
    rule_set_id BIGINT REFERENCES rule_sets(id),
    input_snapshot TEXT NOT NULL,
    output_snapshot TEXT NOT NULL,
    waterfall_trace TEXT,
    rules_fired TEXT,
    execution_time_ms BIGINT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_by VARCHAR(255)
);

-- Indexes
CREATE INDEX idx_customer_external_id ON customers(external_id);
CREATE INDEX idx_customer_segment ON customers(segment);
CREATE INDEX idx_customer_region ON customers(region);
CREATE INDEX idx_customer_not_deleted ON customers(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_pricing_product_external_id ON pricing_products(external_id);
CREATE INDEX idx_pricing_product_type ON pricing_products(product_type);
CREATE INDEX idx_pricing_product_not_deleted ON pricing_products(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_rule_set_status ON rule_sets(status);
CREATE INDEX idx_rule_set_effective ON rule_sets(effective_date);
CREATE INDEX idx_rule_set_engine ON rule_sets(engine_type);
CREATE INDEX idx_rule_set_not_deleted ON rule_sets(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_rule_rule_set ON rules(rule_set_id);
CREATE INDEX idx_rule_type ON rules(rule_type);
CREATE INDEX idx_rule_priority ON rules(priority);
CREATE INDEX idx_rule_status ON rules(status);
CREATE INDEX idx_rule_not_deleted ON rules(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_price_book_region ON price_books(region);
CREATE INDEX idx_price_book_default ON price_books(is_default);
CREATE INDEX idx_price_book_not_deleted ON price_books(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_price_book_entry_book ON price_book_entries(price_book_id);
CREATE INDEX idx_price_book_entry_product ON price_book_entries(product_external_id);

CREATE INDEX idx_audit_quote ON pricing_audit_log(quote_external_id);
CREATE INDEX idx_audit_customer ON pricing_audit_log(customer_external_id);
CREATE INDEX idx_audit_executed ON pricing_audit_log(executed_at);
