-- DRG-582. Additive document apply ledger. Next free CAM version after V1_11.
-- Replay key (document_id, unit_no, apply_key_digest). The same digest is a no-op.
-- Uniqueness of (document_id, unit_no) makes a different digest a conflict, not a second row.
-- Legacy applied acceptance storage is not changed.
-- Guarded rollback (not a Flyway version): db/rollback/V1_12__drop_document_apply_ledger.sql

CREATE TABLE document_apply_ledger (
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    unit_no integer NOT NULL,
    apply_key_digest varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT pk_document_apply_ledger PRIMARY KEY (document_id, unit_no),
    CONSTRAINT uq_document_apply_ledger_replay UNIQUE (document_id, unit_no, apply_key_digest),
    CONSTRAINT chk_document_apply_ledger_unit CHECK (unit_no >= 1),
    CONSTRAINT chk_document_apply_ledger_digest CHECK (apply_key_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_document_apply_ledger_tenant_document
    ON document_apply_ledger (tenant_id, document_id);
