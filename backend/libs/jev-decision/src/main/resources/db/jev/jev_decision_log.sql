-- Canonical DDL for the per-service Jev shadow decision log (ADR-015, ADR-008).
-- NOT run from here. Each participating service copies this verbatim into its own Flyway migration
-- (crm V3, approval-routing V1_3, quote V1_5) so the table lives in that service's schema.
-- Holds allow-listed features only; never a raw request or response payload.

CREATE TABLE jev_decision_log (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID         NOT NULL DEFAULT gen_random_uuid(),

    use_case                VARCHAR(64)  NOT NULL,
    subject_type            VARCHAR(64)  NOT NULL,
    subject_id              VARCHAR(64)  NOT NULL,
    question_id             VARCHAR(64)  NOT NULL,
    question_type           VARCHAR(16)  NOT NULL,

    -- Pinned evaluator configuration. No defaults: a row must say how it was produced.
    evaluator_model         VARCHAR(128) NOT NULL,
    rubric_version          VARCHAR(64)  NOT NULL,
    transport               VARCHAR(32)  NOT NULL,
    wire_format             VARCHAR(32)  NOT NULL,

    feature_schema          VARCHAR(64)  NOT NULL,
    feature_schema_version  INTEGER      NOT NULL,
    features_hash           CHAR(64)     NOT NULL,
    features                JSONB,

    answer_value            VARCHAR(64),
    confidence              NUMERIC(5, 4),
    probabilities           JSONB,

    authoritative_outcome   VARCHAR(64),
    authoritative_source    VARCHAR(8)   NOT NULL,
    agreement               BOOLEAN,

    status                  VARCHAR(16)  NOT NULL,
    error_class             VARCHAR(64),
    latency_ms              BIGINT       NOT NULL DEFAULT 0,
    attempts                INTEGER      NOT NULL DEFAULT 0,
    usage                   JSONB,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_jev_log_source CHECK (authoritative_source IN ('RULE', 'USER', 'NONE'))
);

CREATE INDEX idx_jev_log_use_case_created ON jev_decision_log (use_case, created_at);
CREATE INDEX idx_jev_log_subject ON jev_decision_log (subject_type, subject_id);
CREATE INDEX idx_jev_log_features_hash ON jev_decision_log (use_case, features_hash);
