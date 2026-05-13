-- =====================================================
-- Flyway Migration V1_1: Create Approval Routing Schema
-- =====================================================
-- Description: Approval requests with state machine transitions
-- =====================================================

-- Approval requests table
CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id VARCHAR(255) NOT NULL,
    line_id VARCHAR(255),
    approval_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_discount DECIMAL(19, 2),
    margin_delta DECIMAL(19, 4),
    currency_code VARCHAR(3),
    requester_id VARCHAR(255) NOT NULL,
    approver_id VARCHAR(255),
    approval_tier VARCHAR(50),
    reason TEXT,
    policy_evaluation_snapshot TEXT,
    rejection_reason TEXT,
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_approval_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT chk_approval_type CHECK (approval_type IN (
        'DISCOUNT', 'MARGIN', 'TERMS', 'PRICING_EXCEPTION', 'CONTRACT_MODIFICATION'
    ))
);

CREATE INDEX idx_approval_quote ON approval_requests(quote_id);
CREATE INDEX idx_approval_status ON approval_requests(status);
CREATE INDEX idx_approval_type ON approval_requests(approval_type);

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
