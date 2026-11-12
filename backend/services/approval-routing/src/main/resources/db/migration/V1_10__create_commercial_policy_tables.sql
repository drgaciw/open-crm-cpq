-- =====================================================
-- Flyway Migration V1_10: Commercial Policy Definition
-- =====================================================
-- Description: Declarative, versioned commercial policies (BC-04-01, DRG-441) with their
--              discount %/margin floor/term/non-standard-clause thresholds.
-- =====================================================

CREATE TABLE commercial_policies (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    policy_version INTEGER NOT NULL DEFAULT 1,
    supersedes_id BIGINT,
    segment VARCHAR(100),
    product_family VARCHAR(100),
    region VARCHAR(100),
    effective_date DATE,
    expiration_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_commercial_policy_status CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    CONSTRAINT fk_commercial_policy_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES commercial_policies (id)
);

CREATE INDEX idx_commercial_policy_status ON commercial_policies (status);
CREATE INDEX idx_commercial_policy_segment ON commercial_policies (segment);
CREATE INDEX idx_commercial_policy_product_family ON commercial_policies (product_family);
CREATE INDEX idx_commercial_policy_region ON commercial_policies (region);

CREATE TABLE policy_thresholds (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    policy_id BIGINT NOT NULL,
    threshold_type VARCHAR(30) NOT NULL,
    limit_value DECIMAL(19, 4),
    clause_code VARCHAR(100),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_policy_threshold_type CHECK (threshold_type IN (
        'DISCOUNT_PERCENT', 'MARGIN_FLOOR', 'TERM_MONTHS', 'NON_STANDARD_CLAUSE'
    )),
    CONSTRAINT fk_policy_threshold_policy FOREIGN KEY (policy_id)
        REFERENCES commercial_policies (id) ON DELETE CASCADE
);

CREATE INDEX idx_policy_threshold_policy ON policy_thresholds (policy_id);
CREATE INDEX idx_policy_threshold_type ON policy_thresholds (threshold_type);
