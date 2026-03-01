-- Configuration sessions table
CREATE TABLE configuration_sessions (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    validation_errors TEXT,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
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

-- Outbox events table for event publishing
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    error_message VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_published ON outbox_events(published);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);
