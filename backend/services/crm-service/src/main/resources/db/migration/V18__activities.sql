-- DRG-520 / BC-12-06 activity timeline (ADR-030).
-- NOTE and TASK are written by /api/v1/activities. EMAIL and MEETING stay in the
-- type check so mailbox ingestion (DRG-521) can insert them later. This migration
-- does not create mailbox, thread, or calendar tables, and it stores no message body
-- for those types.

CREATE TABLE activities (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    type VARCHAR(16) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    organization_id BIGINT REFERENCES organizations(id),
    contact_id BIGINT REFERENCES contacts(id),
    deal_id BIGINT REFERENCES deals(id),
    author_id VARCHAR(255),
    author_kind VARCHAR(16),
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    occurred_at TIMESTAMPTZ NOT NULL,
    email_thread_ref VARCHAR(512),
    calendar_event_ref VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_activities_type CHECK (type IN ('NOTE', 'TASK', 'EMAIL', 'MEETING')),
    CONSTRAINT ck_activities_author_kind CHECK (
        author_kind IS NULL OR author_kind IN ('USER', 'SERVICE')
    ),
    CONSTRAINT ck_activities_parent CHECK (
        organization_id IS NOT NULL
        OR contact_id IS NOT NULL
        OR deal_id IS NOT NULL
    ),
    CONSTRAINT ck_activities_due_on_task CHECK (type = 'TASK' OR due_at IS NULL),
    CONSTRAINT ck_activities_completed_on_task CHECK (type = 'TASK' OR completed_at IS NULL),
    CONSTRAINT ck_activities_note_body CHECK (
        type <> 'NOTE' OR length(btrim(coalesce(body, ''))) > 0
    ),
    CONSTRAINT ck_activities_task_subject CHECK (
        type <> 'TASK' OR length(btrim(coalesce(subject, ''))) > 0
    )
);

CREATE INDEX idx_activities_org_timeline
    ON activities (organization_id, occurred_at DESC, id DESC)
    WHERE is_deleted = FALSE AND organization_id IS NOT NULL;

CREATE INDEX idx_activities_contact_timeline
    ON activities (contact_id, occurred_at DESC, id DESC)
    WHERE is_deleted = FALSE AND contact_id IS NOT NULL;

CREATE INDEX idx_activities_deal_timeline
    ON activities (deal_id, occurred_at DESC, id DESC)
    WHERE is_deleted = FALSE AND deal_id IS NOT NULL;

COMMENT ON TABLE activities IS
    'DRG-520 timeline row. At least one of organization, contact, or deal is required.';
COMMENT ON COLUMN activities.email_thread_ref IS
    'Opaque thread link reserved for mailbox ingestion (DRG-521). Not written by the note/task API.';
COMMENT ON COLUMN activities.calendar_event_ref IS
    'Opaque event link reserved for calendar ingestion (DRG-521). Not written by the note/task API.';
COMMENT ON COLUMN activities.author_id IS
    'Verified caller id from the security context (ADR-016). Never taken from the request body.';
