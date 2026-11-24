-- =====================================================
-- Flyway Migration V1_13: Approval matrix (BC-04-03, DRG-443)
-- =====================================================
-- Versioned breach-to-role matrix, plus the columns a routed request needs
-- (tenant, bound matrix version, and step). Legacy rows stay valid: the new columns are nullable.
-- The approver tier is taken from the matrix. The submit body has no tier.
-- =====================================================
-- Rollback: db/rollback/V1_14__drop_approval_matrix.sql

ALTER TABLE approval_requests ADD COLUMN tenant_id VARCHAR(64);
ALTER TABLE approval_requests ADD COLUMN matrix_id BIGINT;
ALTER TABLE approval_requests ADD COLUMN matrix_version INTEGER;
ALTER TABLE approval_requests ADD COLUMN quote_version VARCHAR(64);
ALTER TABLE approval_requests ADD COLUMN routing_step INTEGER;
ALTER TABLE approval_requests ADD COLUMN step_mode VARCHAR(16);
ALTER TABLE approval_requests ADD COLUMN breach_types VARCHAR(200);

ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_matrix_step CHECK (
    (matrix_id IS NULL AND matrix_version IS NULL AND routing_step IS NULL AND step_mode IS NULL)
    OR (matrix_id IS NOT NULL AND matrix_version IS NOT NULL AND routing_step IS NOT NULL
        AND routing_step >= 1 AND step_mode IN ('SERIAL', 'PARALLEL'))
);

CREATE INDEX idx_approval_tenant_status ON approval_requests (tenant_id, status);

CREATE TABLE approval_matrices (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    matrix_version INTEGER NOT NULL DEFAULT 1,
    supersedes_id BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_approval_matrix_status CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    CONSTRAINT fk_approval_matrix_supersedes FOREIGN KEY (supersedes_id) REFERENCES approval_matrices (id)
);

CREATE INDEX idx_approval_matrix_tenant_status ON approval_matrices (tenant_id, status);

CREATE TABLE approval_matrix_rules (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    matrix_id BIGINT NOT NULL,
    breach_type VARCHAR(30) NOT NULL,
    amount_min DECIMAL(19, 4),
    amount_max DECIMAL(19, 4),
    step_order INTEGER NOT NULL,
    step_mode VARCHAR(16) NOT NULL,
    approver_role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_approval_matrix_rule_type CHECK (breach_type IN (
        'DISCOUNT_PERCENT', 'MARGIN_FLOOR', 'TERM_MONTHS', 'NON_STANDARD_CLAUSE'
    )),
    CONSTRAINT chk_approval_matrix_rule_mode CHECK (step_mode IN ('SERIAL', 'PARALLEL')),
    CONSTRAINT chk_approval_matrix_rule_role CHECK (approver_role IN ('DEAL_DESK', 'SALES_VP', 'CFO')),
    CONSTRAINT chk_approval_matrix_rule_order CHECK (step_order >= 1),
    CONSTRAINT chk_approval_matrix_rule_band CHECK (
        amount_min IS NULL OR amount_max IS NULL OR amount_min < amount_max
    ),
    CONSTRAINT fk_approval_matrix_rule_matrix FOREIGN KEY (matrix_id)
        REFERENCES approval_matrices (id) ON DELETE CASCADE
);

CREATE INDEX idx_approval_matrix_rule_matrix ON approval_matrix_rules (matrix_id);

ALTER TABLE approval_requests
    ADD CONSTRAINT fk_approval_request_matrix FOREIGN KEY (matrix_id) REFERENCES approval_matrices (id);
