-- =====================================================
-- Flyway Migration V1_2: Create committed configuration snapshots
-- =====================================================
-- Description: Immutable committed configuration snapshots and selections
-- =====================================================

CREATE TABLE committed_configurations (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    session_id BIGINT NOT NULL UNIQUE,
    product_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (session_id) REFERENCES configuration_sessions(id)
);

CREATE INDEX idx_committed_config_session ON committed_configurations(session_id);
CREATE INDEX idx_committed_config_product ON committed_configurations(product_id);
CREATE INDEX idx_committed_config_user ON committed_configurations(user_id);

CREATE TABLE committed_configuration_selections (
    configuration_id BIGINT NOT NULL,
    option_key VARCHAR(255) NOT NULL,
    option_value VARCHAR(255),
    PRIMARY KEY (configuration_id, option_key),
    FOREIGN KEY (configuration_id) REFERENCES committed_configurations(id) ON DELETE CASCADE
);
