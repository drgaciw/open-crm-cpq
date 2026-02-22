-- Ensure required extensions exist (must be in public or search_path)
CREATE EXTENSION IF NOT EXISTS "pg_trgm" SCHEMA public;

-- Set search path to crm schema
SET search_path TO crm;

-- =====================================================
-- ORGANIZATIONS TABLE
-- =====================================================
-- Must be created first since contacts and other tables reference it
CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    website VARCHAR(500),
    website_domain VARCHAR(255),
    industry VARCHAR(255),
    employee_count INTEGER,
    annual_revenue BIGINT,
    phone VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(255),
    country VARCHAR(255),
    description TEXT,
    linkedin_url VARCHAR(500),
    type VARCHAR(30) DEFAULT 'PROSPECT',
    owner_id VARCHAR(255),
    territory VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- =====================================================
-- CONTACTS TABLE
-- =====================================================
CREATE TABLE contacts (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    mobile VARCHAR(50),
    job_title VARCHAR(255),
    department VARCHAR(255),
    organization_id BIGINT REFERENCES organizations(id),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    postal_code VARCHAR(255),
    country VARCHAR(255),
    linkedin_url VARCHAR(500),
    notes TEXT,
    is_primary BOOLEAN DEFAULT false,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    owner_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- =====================================================
-- LEADS TABLE
-- =====================================================
CREATE TABLE leads (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    contact_id BIGINT REFERENCES contacts(id),
    organization_id BIGINT REFERENCES organizations(id),
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    source VARCHAR(255),
    source_detail VARCHAR(255),
    estimated_value DECIMAL(19,2),
    currency VARCHAR(3) DEFAULT 'USD',
    score INTEGER DEFAULT 0,
    description TEXT,
    owner_id VARCHAR(255),
    assigned_at TIMESTAMPTZ,
    first_contacted_at TIMESTAMPTZ,
    last_contacted_at TIMESTAMPTZ,
    converted_at TIMESTAMPTZ,
    converted_deal_id BIGINT,
    lost_reason VARCHAR(500),
    lost_at TIMESTAMPTZ,
    campaign VARCHAR(255),
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    next_action VARCHAR(500),
    next_action_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- =====================================================
-- DEALS TABLE
-- =====================================================
CREATE TABLE deals (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    organization_id BIGINT REFERENCES organizations(id),
    primary_contact_id BIGINT REFERENCES contacts(id),
    stage VARCHAR(30) NOT NULL DEFAULT 'PROSPECTING',
    amount DECIMAL(19,2),
    currency VARCHAR(3) DEFAULT 'USD',
    probability INTEGER DEFAULT 10,
    expected_close_date DATE,
    actual_close_date DATE,
    description TEXT,
    owner_id VARCHAR(255),
    source_lead_id BIGINT,
    type VARCHAR(30) DEFAULT 'NEW_BUSINESS',
    lost_reason VARCHAR(500),
    won_at TIMESTAMPTZ,
    lost_at TIMESTAMPTZ,
    next_step VARCHAR(500),
    next_step_date DATE,
    competitors VARCHAR(1000),
    weighted_amount DECIMAL(19,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

-- =====================================================
-- INDEXES FOR CONTACTS TABLE
-- =====================================================
CREATE INDEX idx_contact_email ON contacts(email);
CREATE INDEX idx_contact_org ON contacts(organization_id);
CREATE INDEX idx_contact_name ON contacts(first_name, last_name);

-- GIN index for fuzzy text search on contact names using pg_trgm
CREATE INDEX idx_contact_name_trgm ON contacts USING gin((first_name || ' ' || last_name) public.gin_trgm_ops);

-- =====================================================
-- INDEXES FOR ORGANIZATIONS TABLE
-- =====================================================
CREATE INDEX idx_org_name ON organizations(name);
CREATE INDEX idx_org_domain ON organizations(website_domain);
CREATE INDEX idx_org_industry ON organizations(industry);

-- GIN index for fuzzy text search on organization names using pg_trgm
CREATE INDEX idx_org_name_trgm ON organizations USING gin(name public.gin_trgm_ops);

-- =====================================================
-- INDEXES FOR LEADS TABLE
-- =====================================================
CREATE INDEX idx_lead_status ON leads(status);
CREATE INDEX idx_lead_owner ON leads(owner_id);
CREATE INDEX idx_lead_source ON leads(source);
CREATE INDEX idx_lead_created ON leads(created_at);

-- =====================================================
-- INDEXES FOR DEALS TABLE
-- =====================================================
CREATE INDEX idx_deal_stage ON deals(stage);
CREATE INDEX idx_deal_owner ON deals(owner_id);
CREATE INDEX idx_deal_close_date ON deals(expected_close_date);
CREATE INDEX idx_deal_org ON deals(organization_id);

-- =====================================================
-- FOREIGN KEY: leads.converted_deal_id -> deals.id
-- =====================================================
ALTER TABLE leads
    ADD CONSTRAINT fk_leads_converted_deal
    FOREIGN KEY (converted_deal_id) REFERENCES deals(id);

-- =====================================================
-- VIEWS FOR DASHBOARD ANALYTICS
-- =====================================================
CREATE VIEW deal_pipeline_summary AS
SELECT
    stage,
    COUNT(*) AS deal_count,
    COALESCE(SUM(amount), 0) AS total_amount,
    COALESCE(SUM(weighted_amount), 0) AS total_weighted_amount,
    COALESCE(AVG(probability), 0) AS avg_probability
FROM deals
WHERE is_deleted = FALSE AND stage NOT IN ('WON', 'LOST')
GROUP BY stage;

CREATE VIEW lead_funnel_summary AS
SELECT
    status,
    COUNT(*) AS lead_count,
    COALESCE(SUM(estimated_value), 0) AS total_estimated_value,
    COALESCE(AVG(score), 0) AS avg_score
FROM leads
WHERE is_deleted = FALSE
GROUP BY status;
