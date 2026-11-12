-- DRG-455: tenant-scoped clause library. Content changes create new revisions.
-- Migration review required from @drgamtd0 (CODEOWNERS).
CREATE TABLE clause_definitions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    clause_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, clause_key),
    UNIQUE (tenant_id, id, clause_key)
);

CREATE TABLE clause_versions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    clause_id BIGINT NOT NULL,
    clause_key VARCHAR(100) NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    title VARCHAR(255) NOT NULL,
    body_content TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    condition_definition TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (clause_id, version_number),
    UNIQUE (tenant_id, id, clause_id, clause_key, version_number),
    FOREIGN KEY (tenant_id, clause_id, clause_key) REFERENCES clause_definitions(tenant_id, id, clause_key),
    CHECK (status <> 'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE INDEX idx_clause_versions_selection ON clause_versions(tenant_id, status, sort_order, clause_key, version_number DESC);

-- Legacy jobs are deliberately not backfilled with an invented tenant.
-- Only new authenticated rendition generation creates this tenant-owned association.
CREATE TABLE document_renditions (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    job_id BIGINT NOT NULL UNIQUE REFERENCES document_generation_jobs(id),
    selection_context TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, id)
);

CREATE TABLE rendition_selected_clauses (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(100) NOT NULL,
    rendition_id BIGINT NOT NULL,
    clause_id BIGINT NOT NULL,
    clause_version_id BIGINT NOT NULL,
    clause_key VARCHAR(100) NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    title VARCHAR(255) NOT NULL,
    body_content TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (rendition_id, clause_id),
    FOREIGN KEY (tenant_id, rendition_id) REFERENCES document_renditions(tenant_id, id),
    FOREIGN KEY (tenant_id, clause_version_id, clause_id, clause_key, version_number)
        REFERENCES clause_versions(tenant_id, id, clause_id, clause_key, version_number)
);
CREATE INDEX idx_rendition_selected_clauses_order ON rendition_selected_clauses(tenant_id, rendition_id, sort_order, clause_key);
