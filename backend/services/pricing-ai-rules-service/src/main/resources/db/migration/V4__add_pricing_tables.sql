CREATE TABLE IF NOT EXISTS pricing_ai_rules.product (
    id BIGINT PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    product_type VARCHAR(100) NOT NULL,
    category VARCHAR(100),
    list_price DECIMAL(19, 4),
    currency VARCHAR(10) DEFAULT 'USD',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.product_bundle (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    bundle_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.product_bundle_item (
    id BIGINT PRIMARY KEY,
    bundle_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_bundle_item_bundle
        FOREIGN KEY (bundle_id) REFERENCES pricing_ai_rules.product_bundle (id),
    CONSTRAINT fk_pricing_ai_rules_bundle_item_product
        FOREIGN KEY (product_id) REFERENCES pricing_ai_rules.product (id)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.quote (
    id BIGINT PRIMARY KEY,
    external_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    quote_name VARCHAR(255),
    subscription_type VARCHAR(100),
    duration_in_months INTEGER,
    monthly_price DECIMAL(19, 4),
    base_price DECIMAL(19, 4),
    final_price DECIMAL(19, 4),
    currency VARCHAR(10) DEFAULT 'USD',
    discount_rate DECIMAL(5, 4),
    price_multiplier DECIMAL(5, 4),
    status VARCHAR(50) NOT NULL,
    arr DECIMAL(19, 4),
    tcv DECIMAL(19, 4),
    acv DECIMAL(19, 4),
    clv DECIMAL(19, 4),
    user_count INTEGER,
    effective_date DATE,
    expiration_date DATE,
    expected_duration INTEGER,
    is_multi_region BOOLEAN DEFAULT FALSE,
    has_custom_support BOOLEAN DEFAULT FALSE,
    churn_adjustment DECIMAL(5, 4),
    metadata JSONB,
    discount_amount DECIMAL(19, 4),
    satisfaction_score DOUBLE PRECISION,
    feedback_text VARCHAR(1000),
    feedback_date TIMESTAMP WITH TIME ZONE,
    would_recommend BOOLEAN,
    feedback_source VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_quote_customer
        FOREIGN KEY (customer_id) REFERENCES pricing_ai_rules.customer (id)
);

CREATE TABLE IF NOT EXISTS pricing_ai_rules.quote_line_item (
    id BIGINT PRIMARY KEY,
    quote_id BIGINT NOT NULL,
    product_id BIGINT,
    bundle_id BIGINT,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(19, 4),
    list_price DECIMAL(19, 4),
    discount_rate DECIMAL(5, 4),
    final_price DECIMAL(19, 4),
    currency VARCHAR(10) DEFAULT 'USD',
    line_item_type VARCHAR(50) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_ai_rules_line_item_quote
        FOREIGN KEY (quote_id) REFERENCES pricing_ai_rules.quote (id),
    CONSTRAINT fk_pricing_ai_rules_line_item_product
        FOREIGN KEY (product_id) REFERENCES pricing_ai_rules.product (id),
    CONSTRAINT fk_pricing_ai_rules_line_item_bundle
        FOREIGN KEY (bundle_id) REFERENCES pricing_ai_rules.product_bundle (id),
    CONSTRAINT ck_pricing_ai_rules_product_or_bundle CHECK (
        (product_id IS NOT NULL AND bundle_id IS NULL) OR
        (bundle_id IS NOT NULL AND product_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_product_external_id
    ON pricing_ai_rules.product (external_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_product_type
    ON pricing_ai_rules.product (product_type);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_bundle_type
    ON pricing_ai_rules.product_bundle (bundle_type);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_bundle_status
    ON pricing_ai_rules.product_bundle (status);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_quote_external_id
    ON pricing_ai_rules.quote (external_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_quote_customer_id
    ON pricing_ai_rules.quote (customer_id);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_quote_status
    ON pricing_ai_rules.quote (status);
CREATE INDEX IF NOT EXISTS idx_pricing_ai_rules_line_item_quote_id
    ON pricing_ai_rules.quote_line_item (quote_id);
