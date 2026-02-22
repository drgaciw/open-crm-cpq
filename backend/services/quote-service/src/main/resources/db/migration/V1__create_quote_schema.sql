-- =====================================================
-- Flyway Migration V1: Create Quote Schema
-- =====================================================
-- Description: Initial schema creation for quote-service
-- Tables: quotes, quote_line_items, approval_requests, quote_documents, quote_status_history
-- =====================================================

SET search_path TO quote;

-- =====================================================
-- TABLE: quotes
-- =====================================================
-- Quote header table containing all main quote information
-- =====================================================

CREATE TABLE quotes (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_number VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    customer_external_id VARCHAR(255) NOT NULL,
    deal_external_id VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    deal_type VARCHAR(30) NOT NULL DEFAULT 'NEW',
    subscription_type VARCHAR(30),
    currency VARCHAR(3) DEFAULT 'USD',
    region VARCHAR(20),
    duration_in_months INT DEFAULT 12,
    start_date DATE,
    end_date DATE,
    subtotal DECIMAL(19,2) DEFAULT 0,
    total_discount DECIMAL(19,2) DEFAULT 0,
    total_tax DECIMAL(19,2) DEFAULT 0,
    total_amount DECIMAL(19,2) DEFAULT 0,
    arr DECIMAL(19,2),
    tcv DECIMAL(19,2),
    acv DECIMAL(19,2),
    approval_state VARCHAR(30) DEFAULT 'NONE',
    approval_tier VARCHAR(50),
    pricing_waterfall_json TEXT,
    notes TEXT,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Check constraints for enum values
    CONSTRAINT chk_quotes_status CHECK (status IN (
        'DRAFT', 'IN_REVIEW', 'PENDING_APPROVAL', 'APPROVED',
        'SENT', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'REJECTED'
    )),
    CONSTRAINT chk_quotes_deal_type CHECK (deal_type IN (
        'NEW', 'AMENDMENT', 'RENEWAL', 'EXPANSION'
    )),
    CONSTRAINT chk_quotes_subscription_type CHECK (subscription_type IS NULL OR subscription_type IN (
        'SAAS', 'ONPREM', 'HYBRID'
    )),
    CONSTRAINT chk_quotes_approval_state CHECK (approval_state IN (
        'NONE', 'PENDING', 'APPROVED', 'REJECTED'
    ))
);

-- Indexes for quotes table
CREATE INDEX idx_quotes_quote_number ON quotes(quote_number);
CREATE INDEX idx_quotes_status ON quotes(status);
CREATE INDEX idx_quotes_deal_type ON quotes(deal_type);
CREATE INDEX idx_quotes_customer_external_id ON quotes(customer_external_id);
CREATE INDEX idx_quotes_deal_external_id ON quotes(deal_external_id);
CREATE INDEX idx_quotes_approval_state ON quotes(approval_state);
CREATE INDEX idx_quotes_created_at ON quotes(created_at);
CREATE INDEX idx_quotes_is_deleted ON quotes(is_deleted);

-- =====================================================
-- TABLE: quote_line_items
-- =====================================================
-- Line items within a quote
-- =====================================================

CREATE TABLE quote_line_items (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id BIGINT NOT NULL REFERENCES quotes(id),
    product_external_id VARCHAR(255) NOT NULL,
    product_name VARCHAR(255),
    product_type VARCHAR(50),
    line_item_type VARCHAR(50),
    quantity INT NOT NULL DEFAULT 1,
    unit_of_measure VARCHAR(50),
    list_price DECIMAL(19,2) NOT NULL,
    unit_price DECIMAL(19,2),
    discount_rate DECIMAL(10,4) DEFAULT 0,
    discount_amount DECIMAL(19,2) DEFAULT 0,
    net_price DECIMAL(19,2),
    extended_amount DECIMAL(19,2),
    billing_frequency VARCHAR(20),
    sort_order INT DEFAULT 0,
    pricing_explanation_code VARCHAR(100),
    pricing_waterfall_json TEXT,
    notes TEXT,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Check constraints
    CONSTRAINT chk_quote_line_items_quantity CHECK (quantity > 0)
);

-- Indexes for quote_line_items table
CREATE INDEX idx_quote_line_items_quote_id ON quote_line_items(quote_id);
CREATE INDEX idx_quote_line_items_product_external_id ON quote_line_items(product_external_id);
CREATE INDEX idx_quote_line_items_sort_order ON quote_line_items(quote_id, sort_order);
CREATE INDEX idx_quote_line_items_is_deleted ON quote_line_items(is_deleted);

-- =====================================================
-- TABLE: approval_requests
-- =====================================================
-- Approval workflow records
-- =====================================================

CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id BIGINT NOT NULL REFERENCES quotes(id),
    tier VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(100) NOT NULL,
    approver VARCHAR(100),
    comments TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Check constraints for enum values
    CONSTRAINT chk_approval_requests_tier CHECK (tier IN (
        'DEAL_DESK', 'SALES_VP', 'CFO'
    )),
    CONSTRAINT chk_approval_requests_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED'
    ))
);

-- Indexes for approval_requests table
CREATE INDEX idx_approval_requests_quote_id ON approval_requests(quote_id);
CREATE INDEX idx_approval_requests_tier ON approval_requests(tier);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_approver ON approval_requests(approver);
CREATE INDEX idx_approval_requests_requested_at ON approval_requests(requested_at);
CREATE INDEX idx_approval_requests_is_deleted ON approval_requests(is_deleted);

-- =====================================================
-- TABLE: quote_documents
-- =====================================================
-- Generated PDF documents
-- =====================================================

CREATE TABLE quote_documents (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id BIGINT NOT NULL REFERENCES quotes(id),
    document_type VARCHAR(30) NOT NULL DEFAULT 'PROPOSAL',
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) DEFAULT 'application/pdf',
    content BYTEA,
    file_size BIGINT,
    generated_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Check constraints for enum values
    CONSTRAINT chk_quote_documents_document_type CHECK (document_type IN (
        'PROPOSAL', 'ORDER_FORM', 'SOW'
    ))
);

-- Indexes for quote_documents table
CREATE INDEX idx_quote_documents_quote_id ON quote_documents(quote_id);
CREATE INDEX idx_quote_documents_document_type ON quote_documents(document_type);
CREATE INDEX idx_quote_documents_created_at ON quote_documents(created_at);
CREATE INDEX idx_quote_documents_is_deleted ON quote_documents(is_deleted);

-- =====================================================
-- TABLE: quote_status_history
-- =====================================================
-- Audit trail of status changes
-- =====================================================

CREATE TABLE quote_status_history (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    quote_id BIGINT NOT NULL REFERENCES quotes(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(100),
    reason TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Check constraints for enum values
    CONSTRAINT chk_quote_status_history_from_status CHECK (from_status IS NULL OR from_status IN (
        'DRAFT', 'IN_REVIEW', 'PENDING_APPROVAL', 'APPROVED',
        'SENT', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'REJECTED'
    )),
    CONSTRAINT chk_quote_status_history_to_status CHECK (to_status IN (
        'DRAFT', 'IN_REVIEW', 'PENDING_APPROVAL', 'APPROVED',
        'SENT', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'REJECTED'
    ))
);

-- Indexes for quote_status_history table
CREATE INDEX idx_quote_status_history_quote_id ON quote_status_history(quote_id);
CREATE INDEX idx_quote_status_history_to_status ON quote_status_history(to_status);
CREATE INDEX idx_quote_status_history_changed_at ON quote_status_history(changed_at);
CREATE INDEX idx_quote_status_history_is_deleted ON quote_status_history(is_deleted);

-- =====================================================
-- END OF MIGRATION V1
-- =====================================================
