-- S3 / DRG-581. Nullable pins, digests and current totals for an unpriced revision,
-- plus the append-only commercial envelope and the operation receipt.
-- Existing rows stay valid. This migration does not fabricate historical values.
--
-- ROLLBACK (not executed by Flyway): SELECT amendment_unpriced_storage_v1_10_rollback();
-- The function refuses while any affected row is null and does not invent pins, digests or amounts.

ALTER TABLE amendment_documents ALTER COLUMN content_digest DROP NOT NULL;
ALTER TABLE amendment_documents ALTER COLUMN tcv_delta DROP NOT NULL;
ALTER TABLE amendment_documents ALTER COLUMN arr_delta DROP NOT NULL;
ALTER TABLE amendment_documents ALTER COLUMN one_time_total DROP NOT NULL;
ALTER TABLE amendment_documents ALTER COLUMN credit_total DROP NOT NULL;

ALTER TABLE amendment_document_revisions ALTER COLUMN content_digest DROP NOT NULL;

ALTER TABLE amendment_document_lines ALTER COLUMN product_external_id DROP NOT NULL;
ALTER TABLE amendment_document_lines ALTER COLUMN pricebook_version DROP NOT NULL;
ALTER TABLE amendment_document_lines ALTER COLUMN configuration_hash DROP NOT NULL;

CREATE TABLE amendment_commercial_envelopes (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    revision_no integer NOT NULL,
    envelope_schema_version integer NOT NULL,
    envelope_digest varchar(64) NOT NULL,
    canonical_bytes bytea NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    CONSTRAINT uq_amendment_envelope_identity UNIQUE (tenant_id, document_id, revision_no, envelope_digest),
    CONSTRAINT fk_amendment_envelope_revision FOREIGN KEY (document_id, revision_no)
        REFERENCES amendment_document_revisions (document_id, revision_no),
    CONSTRAINT chk_amendment_envelope_digest CHECK (envelope_digest ~ '^[0-9a-f]{64}$')
);

CREATE TABLE amendment_operation_receipts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    caller_id varchar(255) NOT NULL,
    operation varchar(80) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    source_lifecycle_change_id uuid NOT NULL,
    source_revision integer NOT NULL,
    request_digest varchar(64) NOT NULL,
    http_status integer NOT NULL,
    response_json text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    CONSTRAINT uq_amendment_receipt_key UNIQUE (tenant_id, caller_id, operation, idempotency_key),
    CONSTRAINT uq_amendment_receipt_natural UNIQUE (
        tenant_id, document_id, source_lifecycle_change_id, source_revision, operation),
    CONSTRAINT chk_amendment_receipt_digest CHECK (request_digest ~ '^[0-9a-f]{64}$')
);

CREATE OR REPLACE FUNCTION reject_commercial_envelope_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'commercial envelope is append-only (op=%)', TG_OP USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_amendment_envelope_append_only
    BEFORE UPDATE OR DELETE ON amendment_commercial_envelopes
    FOR EACH ROW EXECUTE FUNCTION reject_commercial_envelope_mutation();

CREATE OR REPLACE FUNCTION amendment_unpriced_storage_v1_10_rollback()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM amendment_documents
        WHERE content_digest IS NULL OR tcv_delta IS NULL OR arr_delta IS NULL
           OR one_time_total IS NULL OR credit_total IS NULL
    ) OR EXISTS (
        SELECT 1 FROM amendment_document_revisions WHERE content_digest IS NULL
    ) OR EXISTS (
        SELECT 1 FROM amendment_document_lines
        WHERE product_external_id IS NULL OR pricebook_version IS NULL OR configuration_hash IS NULL
    ) THEN
        RAISE EXCEPTION 'rollback refused: affected row remains null; historical values are not fabricated';
    END IF;
    IF EXISTS (SELECT 1 FROM amendment_commercial_envelopes)
       OR EXISTS (SELECT 1 FROM amendment_operation_receipts) THEN
        RAISE EXCEPTION 'rollback refused: storage rows exist; historical values are not fabricated';
    END IF;
    ALTER TABLE amendment_documents ALTER COLUMN content_digest SET NOT NULL;
    ALTER TABLE amendment_documents ALTER COLUMN tcv_delta SET NOT NULL;
    ALTER TABLE amendment_documents ALTER COLUMN arr_delta SET NOT NULL;
    ALTER TABLE amendment_documents ALTER COLUMN one_time_total SET NOT NULL;
    ALTER TABLE amendment_documents ALTER COLUMN credit_total SET NOT NULL;
    ALTER TABLE amendment_document_revisions ALTER COLUMN content_digest SET NOT NULL;
    ALTER TABLE amendment_document_lines ALTER COLUMN product_external_id SET NOT NULL;
    ALTER TABLE amendment_document_lines ALTER COLUMN pricebook_version SET NOT NULL;
    ALTER TABLE amendment_document_lines ALTER COLUMN configuration_hash SET NOT NULL;
    DROP TABLE amendment_operation_receipts;
    DROP TABLE amendment_commercial_envelopes;
END;
$$;
