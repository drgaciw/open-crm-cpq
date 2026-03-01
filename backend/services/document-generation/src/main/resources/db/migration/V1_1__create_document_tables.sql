-- =====================================================
-- Flyway Migration V1_1: Create Document Generation Schema
-- =====================================================
-- Description: Document generation jobs with idempotency
-- =====================================================

-- Document generation jobs table
CREATE TABLE document_generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    document_url VARCHAR(500),
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_doc_job_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT chk_doc_job_type CHECK (document_type IN (
        'QUOTE_PDF', 'CONTRACT_PDF', 'ORDER_FORM', 'INVOICE'
    ))
);

CREATE INDEX idx_doc_job_quote ON document_generation_jobs(quote_id);
CREATE INDEX idx_doc_job_status ON document_generation_jobs(status);
CREATE UNIQUE INDEX idx_doc_job_idempotency ON document_generation_jobs(idempotency_key);

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
