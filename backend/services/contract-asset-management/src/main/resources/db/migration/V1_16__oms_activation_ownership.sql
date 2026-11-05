CREATE TABLE oms_activation_operations (
    operation_key VARCHAR(255) PRIMARY KEY,
    business_key VARCHAR(64) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    scope_id VARCHAR(255) NOT NULL,
    quote_id VARCHAR(255) NOT NULL,
    source_line_id VARCHAR(255) NOT NULL,
    accepted_revision INTEGER NOT NULL CHECK (accepted_revision > 0),
    subscription_id VARCHAR(36) NOT NULL UNIQUE,
    acknowledgment_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oms_activation_inbox (
    event_id VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
