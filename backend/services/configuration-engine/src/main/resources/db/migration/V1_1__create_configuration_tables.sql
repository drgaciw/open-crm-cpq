-- =====================================================
-- Flyway Migration V1_1: Create Configuration Engine Schema
-- =====================================================
-- Description: Configuration sessions and selections tables
-- =====================================================

-- Configuration sessions table
CREATE TABLE configuration_sessions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    product_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    validation_errors TEXT,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_config_session_status CHECK (status IN (
        'IN_PROGRESS', 'COMPLETED', 'EXPIRED', 'CANCELLED'
    ))
);

CREATE INDEX idx_config_session_product ON configuration_sessions(product_id);
CREATE INDEX idx_config_session_user ON configuration_sessions(user_id);
CREATE INDEX idx_config_session_status ON configuration_sessions(status);

-- Configuration selections (element collection)
CREATE TABLE configuration_selections (
    session_id BIGINT NOT NULL,
    option_key VARCHAR(255) NOT NULL,
    option_value VARCHAR(255),
    PRIMARY KEY (session_id, option_key),
    FOREIGN KEY (session_id) REFERENCES configuration_sessions(id) ON DELETE CASCADE
);

-- Outbox events table for Transactional Outbox pattern
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    error_message VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_published ON outbox_events(published);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
