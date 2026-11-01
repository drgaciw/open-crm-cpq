-- DRG-510: transactional outbox for CRM domain events.
-- crm-service had no outbox, so this is the landed shape of the quote and pricing migrations
-- (create table, relay status and leasing, envelope columns, SKIPPED) in one additive file.
CREATE TABLE IF NOT EXISTS outbox_events (
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
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    next_attempt_at TIMESTAMPTZ,
    topic VARCHAR(255),
    event_version INTEGER,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    actor VARCHAR(255),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_published ON outbox_events(published);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished_aggregate
    ON outbox_events (aggregate_type, aggregate_id, id) WHERE status <> 'PUBLISHED';
