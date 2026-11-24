-- DRG-446 / BC-04-06. Comments, threads, and the approval-history timeline.
-- The approval request is the case. Rows are written in the same transaction as
-- ApprovalGranted, ApprovalRejected, and ApprovalEscalated, plus each comment.
-- Quote version is a link stamped by the quote BFF. Tenant isolation is the quote's
-- tenant, resolved in quote-service, not a column on approval_requests (H4).
-- Rollback: db/rollback/V1_17__drop_approval_case_timeline.sql

CREATE TABLE approval_case_comments (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    approval_request_id BIGINT NOT NULL REFERENCES approval_requests(id),
    parent_comment_id BIGINT REFERENCES approval_case_comments(id),
    quote_id VARCHAR(255) NOT NULL,
    quote_version BIGINT,
    author_id VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    commented_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_approval_comment_case
    ON approval_case_comments (approval_request_id, commented_at, id);

CREATE TABLE approval_timeline_entries (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    approval_request_id BIGINT NOT NULL REFERENCES approval_requests(id),
    quote_id VARCHAR(255) NOT NULL,
    quote_version BIGINT,
    comment_id BIGINT REFERENCES approval_case_comments(id),
    parent_comment_id BIGINT,
    entry_kind VARCHAR(32) NOT NULL,
    event_name VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id VARCHAR(255),
    body TEXT,
    from_tier VARCHAR(50),
    to_tier VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_approval_timeline_kind CHECK (entry_kind IN (
        'GRANTED', 'REJECTED', 'ESCALATED', 'COMMENT'
    ))
);

CREATE INDEX idx_approval_timeline_quote
    ON approval_timeline_entries (quote_id, occurred_at, id);
CREATE INDEX idx_approval_timeline_case
    ON approval_timeline_entries (approval_request_id, occurred_at, id);
