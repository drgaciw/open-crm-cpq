-- DRG-512 / ADR-028 evidence-priced fact ledger.
-- Score is computed by crm-service from evidence weights. The model does not send it.
-- PROVENANCE is the POSSIBLE band (stored, not an inbox value).
-- UNRESOLVED is contradicting evidence, kept regardless of score.
-- A human-owned value is not superseded by a later agent proposal.
-- parent_organization_id is not written by this table's apply path.

CREATE TABLE crm_fact (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    fact_value TEXT NOT NULL,
    score NUMERIC(5, 4) NOT NULL,
    band VARCHAR(16),
    method VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    weight_catalog_version VARCHAR(32) NOT NULL,
    writer_kind VARCHAR(16) NOT NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    contradicting BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by VARCHAR(255),
    decided_at TIMESTAMPTZ,
    observed_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_crm_fact_subject_type CHECK (subject_type IN ('CONTACT', 'ORGANIZATION')),
    CONSTRAINT ck_crm_fact_score CHECK (score >= 0 AND score <= 0.99),
    CONSTRAINT ck_crm_fact_band CHECK (band IS NULL OR band IN ('VERIFIED', 'PROBABLE', 'POSSIBLE')),
    CONSTRAINT ck_crm_fact_status CHECK (status IN (
        'PROPOSED',
        'APPLIED',
        'DISMISSED',
        'SUPERSEDED',
        'UNRESOLVED',
        'PROVENANCE'
    )),
    CONSTRAINT ck_crm_fact_writer_kind CHECK (writer_kind IN ('HUMAN', 'AGENT')),
    CONSTRAINT ck_crm_fact_field_key CHECK (length(btrim(field_key)) > 0),
    CONSTRAINT ck_crm_fact_value CHECK (length(btrim(fact_value)) > 0)
);

CREATE TABLE crm_fact_evidence (
    id BIGSERIAL PRIMARY KEY,
    fact_id BIGINT NOT NULL,
    kind VARCHAR(64) NOT NULL,
    weight NUMERIC(5, 4) NOT NULL,
    url VARCHAR(2000),
    observed_at TIMESTAMPTZ,
    asserted_value TEXT,
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_crm_fact_evidence_fact FOREIGN KEY (fact_id) REFERENCES crm_fact (id),
    CONSTRAINT ck_crm_fact_evidence_weight CHECK (weight >= 0 AND weight <= 1),
    CONSTRAINT ck_crm_fact_evidence_kind CHECK (kind IN (
        'EXACT_EMAIL_MATCH',
        'SYNCED_THREAD_REPLY',
        'SIGNATURE_BLOCK',
        'CITED_WEB_ANSWER'
    )),
    CONSTRAINT ck_crm_fact_evidence_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_crm_fact_current
    ON crm_fact (tenant_id, subject_type, subject_id, field_key)
    WHERE is_deleted = FALSE AND superseded_at IS NULL;

CREATE INDEX idx_crm_fact_inbox
    ON crm_fact (tenant_id, subject_type, subject_id, observed_at DESC)
    WHERE is_deleted = FALSE
      AND superseded_at IS NULL
      AND status IN ('PROPOSED', 'UNRESOLVED');

CREATE INDEX idx_crm_fact_evidence_fact ON crm_fact_evidence (fact_id, sort_order);

COMMENT ON TABLE crm_fact IS
    'ADR-028 evidence-priced fact. Score is 1 - product(1 - weight), capped at 0.99, computed in crm-service.';
COMMENT ON COLUMN crm_fact.score IS
    'Server-computed evidence score. Clients cannot supply this column.';
COMMENT ON COLUMN crm_fact.status IS
    'PROVENANCE is POSSIBLE-band storage. UNRESOLVED is contradicting evidence. Neither is a displayed value.';
COMMENT ON COLUMN crm_fact_evidence.weight IS
    'Catalog weight at observation time. Not a model-supplied confidence.';
