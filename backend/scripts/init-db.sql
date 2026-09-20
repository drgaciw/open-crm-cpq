-- Initialize database with schemas for each service
-- This runs when PostgreSQL container starts for the first time

-- Create schemas for service isolation (single DB, multiple schemas)
CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS cpq;
CREATE SCHEMA IF NOT EXISTS pricing;
CREATE SCHEMA IF NOT EXISTS product;
CREATE SCHEMA IF NOT EXISTS quote;
CREATE SCHEMA IF NOT EXISTS communication;
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS configuration;
CREATE SCHEMA IF NOT EXISTS approval;
CREATE SCHEMA IF NOT EXISTS document;
CREATE SCHEMA IF NOT EXISTS contract;

-- Create application user with limited privileges
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'opencrm_app') THEN
        CREATE ROLE opencrm_app WITH LOGIN PASSWORD 'opencrm_app_password';
    END IF;
END
$$;

-- Grant privileges to application user
GRANT USAGE ON SCHEMA crm, cpq, pricing, product, quote, communication, ai TO opencrm_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA crm, cpq, pricing, product, quote, communication, ai TO opencrm_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA crm, cpq, pricing, product, quote, communication, ai TO opencrm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA crm, cpq, pricing, product, quote, communication, ai
    GRANT ALL PRIVILEGES ON TABLES TO opencrm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA crm, cpq, pricing, product, quote, communication, ai
    GRANT ALL PRIVILEGES ON SEQUENCES TO opencrm_app;

-- Enable required extensions
GRANT USAGE ON SCHEMA configuration, approval, document, contract TO opencrm_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA configuration, approval, document, contract TO opencrm_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA configuration, approval, document, contract TO opencrm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA configuration, approval, document, contract
    GRANT ALL PRIVILEGES ON TABLES TO opencrm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA configuration, approval, document, contract
    GRANT ALL PRIVILEGES ON SEQUENCES TO opencrm_app;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";     -- For fuzzy text search
-- pgvector extension (requires pgvector image, uncomment when needed)
-- CREATE EXTENSION IF NOT EXISTS "vector";   -- For AI embeddings (pgvector)

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'OpenCRM database initialized successfully';
END
$$;
