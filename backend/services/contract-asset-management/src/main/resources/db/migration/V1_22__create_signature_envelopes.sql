-- DRG-457 / BC-06-04. E-signature coordination adapter (ACL). An envelope sent to an e-sign provider for signature,
-- and the dedupe ledger that makes GetEnvelopeStatus idempotent under duplicate webhook delivery or polling.
--
-- signature_envelopes stores only our own, provider-agnostic shape: the ACL (com.opencrm.contract.signature.acl)
-- translates whatever the provider returns before it reaches this table. provider_envelope_ref is an opaque string
-- handle for the ACL adapter to use on VoidEnvelope/GetEnvelopeStatus calls; nothing in this schema is specific to
-- any one e-sign vendor.
--
-- signature_envelope_events is the idempotency guard (mirrors the processed_events inbox pattern, ADR-018 D6):
-- one row per (envelope_id, provider_event_id) a webhook delivery or poll result claims. The unique constraint
-- rejects a redelivered or re-sent provider event before any status transition or SignatureCompleted publish
-- happens twice.

CREATE TABLE signature_envelopes (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    provider_envelope_ref VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    void_reason VARCHAR(500),
    sent_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_signature_envelope_status
        CHECK (status IN ('DRAFT', 'SENT', 'VIEWED', 'COMPLETED', 'VOIDED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX idx_signature_envelope_aggregate ON signature_envelopes (aggregate_type, aggregate_id);
CREATE INDEX idx_signature_envelope_provider_ref ON signature_envelopes (provider_envelope_ref);

CREATE TABLE signature_envelope_events (
    id BIGSERIAL PRIMARY KEY,
    envelope_id BIGINT NOT NULL REFERENCES signature_envelopes (id),
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_signature_envelope_event UNIQUE (envelope_id, provider_event_id)
);

CREATE INDEX idx_signature_envelope_event_envelope ON signature_envelope_events (envelope_id);
