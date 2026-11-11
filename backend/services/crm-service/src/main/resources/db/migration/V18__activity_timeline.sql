-- DRG-520 activity timeline, following CRM migrations V16 and V17.
-- Workforce identity is not the commercial customer tenant proposed by ADR-036.
CREATE TABLE activities (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    workforce_tenant_id VARCHAR(128) NOT NULL,
    author_id VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    note TEXT,
    organization_id BIGINT REFERENCES organizations(id),
    contact_id BIGINT REFERENCES contacts(id),
    deal_id BIGINT REFERENCES deals(id),
    external_reference VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_activity_subject CHECK (num_nonnulls(organization_id, contact_id, deal_id) >= 1),
    CONSTRAINT chk_activity_text CHECK (length(btrim(title)) > 0 AND length(btrim(author_id)) > 0
        AND length(btrim(workforce_tenant_id)) > 0 AND (note IS NULL OR length(note) <= 10000)),
    CONSTRAINT chk_activity_type CHECK (type IN ('NOTE','TASK','EMAIL','MEETING')),
    CONSTRAINT chk_activity_task CHECK (type = 'TASK' OR (due_at IS NULL AND completed_at IS NULL)),
    CONSTRAINT chk_activity_reference CHECK (
        (type IN ('EMAIL','MEETING') AND external_reference IS NOT NULL
            AND length(btrim(external_reference)) > 0 AND note IS NULL)
        OR (type IN ('NOTE','TASK') AND external_reference IS NULL)),
    CONSTRAINT chk_activity_note CHECK (type <> 'NOTE' OR (note IS NOT NULL AND length(btrim(note)) > 0))
);
CREATE INDEX idx_activity_organization ON activities (workforce_tenant_id, organization_id, occurred_at DESC, id DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_contact ON activities (workforce_tenant_id, contact_id, occurred_at DESC, id DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_deal ON activities (workforce_tenant_id, deal_id, occurred_at DESC, id DESC) WHERE is_deleted = FALSE;
COMMENT ON TABLE activities IS 'DRG-520 manually recorded timeline. No mailbox bodies or provider credentials. Source is MANUAL.';
