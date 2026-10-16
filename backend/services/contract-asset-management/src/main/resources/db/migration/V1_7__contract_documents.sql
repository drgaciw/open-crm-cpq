-- DRG-579 / RA-45 (S1). Contract-documents schema authorized by ADR-035 (S1 and S2 only).
-- Expand-only: new tables plus nullable columns. No backfill, no precedence_flag (DRG-592),
-- no apply_status (OQ2), no change to applied_quote_acceptances (ledger is V1_9 / DRG-582).
-- Rollback: docs/renewals-amendments/s1-v1-7-contract-documents.md
--
-- Logical char(n) columns are varchar with a length check. PostgreSQL char pads with spaces
-- and that padding breaks SHA-256 equality. Enum tokens are the Java enum names (uppercase).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cam_app') THEN
        CREATE ROLE cam_app NOLOGIN;
    END IF;
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO cam_app', current_schema());
END $$;

CREATE TABLE contracts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    contract_number varchar(128) NOT NULL,
    customer_account_id uuid NOT NULL,
    term_model varchar(40) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    governing_agreement_ref varchar(255),
    status varchar(40) NOT NULL,
    last_executed_seq integer NOT NULL DEFAULT 0,
    prior_contract_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_contracts_tenant_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT fk_contracts_prior FOREIGN KEY (prior_contract_id) REFERENCES contracts (id),
    CONSTRAINT chk_contracts_term_model CHECK (term_model IN ('TERMED', 'EVERGREEN', 'PERPETUAL')),
    CONSTRAINT chk_contracts_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT chk_contracts_dates CHECK (
        (term_model = 'EVERGREEN' AND end_date IS NULL)
        OR (term_model <> 'EVERGREEN' AND end_date IS NOT NULL AND end_date >= start_date)
    ),
    CONSTRAINT chk_contracts_last_executed_seq CHECK (last_executed_seq >= 0)
);

CREATE TABLE amendment_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    contract_id uuid NOT NULL REFERENCES contracts (id),
    lifecycle_change_id uuid NOT NULL,
    lifecycle_change_revision integer NOT NULL,
    intent varchar(40) NOT NULL,
    initiation_trigger varchar(40) NOT NULL,
    request_identifier varchar(255),
    provisional_number varchar(128) NOT NULL,
    amendment_seq integer,
    amendment_number varchar(128),
    document_kind varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    current_revision_no integer NOT NULL,
    executed_revision_no integer,
    signature_policy varchar(40) NOT NULL,
    approval_ref varchar(255),
    signature_envelope_ref varchar(255),
    action_date date NOT NULL,
    contract_effective_date date NOT NULL,
    service_activation_date date,
    customer_acceptance_date date,
    backdating_reason_code varchar(128),
    co_term_date date,
    currency_code varchar(3) NOT NULL,
    tcv_delta numeric(19, 4) NOT NULL,
    arr_delta numeric(19, 4) NOT NULL,
    one_time_total numeric(19, 4) NOT NULL,
    credit_total numeric(19, 4) NOT NULL,
    content_digest varchar(64) NOT NULL,
    rendition_digest varchar(64),
    signed_copy_sha256 varchar(64),
    template_id varchar(255),
    template_version varchar(64),
    supersedes_document_id uuid,
    superseded_by_document_id uuid,
    voids_document_id uuid,
    evidence_class varchar(40) NOT NULL,
    retention_until date,
    legal_hold boolean NOT NULL DEFAULT false,
    expires_at timestamptz,
    executed_at timestamptz,
    applied_at timestamptz,
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_amendment_document_lifecycle UNIQUE (lifecycle_change_id),
    CONSTRAINT uq_amendment_document_seq UNIQUE (contract_id, amendment_seq),
    CONSTRAINT fk_amendment_document_supersedes FOREIGN KEY (supersedes_document_id) REFERENCES amendment_documents (id),
    CONSTRAINT fk_amendment_document_superseded_by FOREIGN KEY (superseded_by_document_id) REFERENCES amendment_documents (id),
    CONSTRAINT fk_amendment_document_voids FOREIGN KEY (voids_document_id) REFERENCES amendment_documents (id),
    CONSTRAINT chk_amendment_document_intent CHECK (intent IN (
        'RENEW', 'AMEND', 'CANCEL', 'RESTRUCTURE', 'TRANSFER', 'SUSPEND', 'RESUME', 'TRUE_UP', 'CONVERT'
    )),
    CONSTRAINT chk_amendment_document_trigger CHECK (initiation_trigger IN (
        'MANUAL', 'LEAD_TIME', 'TERM_END_AUTO', 'THRESHOLD', 'TRUE_UP_CYCLE', 'END_OF_LIFE', 'API'
    )),
    CONSTRAINT chk_amendment_document_kind CHECK (document_kind IN (
        'ORIGINAL', 'AMENDMENT', 'RENEWAL', 'AMENDED_AND_RESTATED', 'TRUE_UP_NOTICE',
        'TERMINATION', 'RESTRUCTURE', 'BACKFILLED'
    )),
    CONSTRAINT chk_amendment_document_status CHECK (status IN (
        'DRAFT', 'IN_REVIEW', 'APPROVED', 'OUT_FOR_SIGNATURE', 'REJECTED', 'EXECUTED',
        'WITHDRAWN', 'APPLIED', 'PARTIALLY_APPLIED', 'APPLY_BLOCKED', 'VOID'
    )),
    CONSTRAINT chk_amendment_document_signature CHECK (signature_policy IN (
        'WET', 'E_SIGN', 'CLICK_THROUGH', 'NOTICE_ONLY', 'NOT_REQUIRED'
    )),
    CONSTRAINT chk_amendment_document_evidence CHECK (evidence_class IN ('STANDARD', 'LEGACY')),
    CONSTRAINT chk_amendment_seq_non_negative CHECK (amendment_seq IS NULL OR amendment_seq >= 0),
    CONSTRAINT chk_amendment_number_with_seq CHECK (
        (amendment_seq IS NULL AND amendment_number IS NULL)
        OR (amendment_seq IS NOT NULL AND amendment_number IS NOT NULL)
    ),
    CONSTRAINT chk_amendment_document_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_amendment_document_digests CHECK (
        content_digest ~ '^[0-9a-f]{64}$'
        AND (rendition_digest IS NULL OR rendition_digest ~ '^[0-9a-f]{64}$')
        AND (signed_copy_sha256 IS NULL OR signed_copy_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT chk_amendment_document_backdating CHECK (
        contract_effective_date >= action_date OR backdating_reason_code IS NOT NULL
    ),
    CONSTRAINT chk_amendment_document_executed_not_deleted CHECK (
        status NOT IN ('EXECUTED', 'APPLIED', 'PARTIALLY_APPLIED', 'APPLY_BLOCKED', 'VOID')
        OR is_deleted = false
    )
);

CREATE UNIQUE INDEX uq_amendment_document_request
    ON amendment_documents (tenant_id, request_identifier)
    WHERE request_identifier IS NOT NULL;

CREATE UNIQUE INDEX uq_amendment_document_envelope
    ON amendment_documents (signature_envelope_ref)
    WHERE signature_envelope_ref IS NOT NULL;

CREATE INDEX idx_amendment_document_contract_status
    ON amendment_documents (contract_id, status);

CREATE INDEX idx_amendment_document_status_expires
    ON amendment_documents (status, expires_at);

CREATE INDEX idx_amendment_document_retention
    ON amendment_documents (tenant_id, legal_hold, retention_until);

CREATE TABLE amendment_document_revisions (
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    revision_no integer NOT NULL,
    tenant_id uuid NOT NULL,
    lifecycle_change_revision integer NOT NULL,
    target_quote_id uuid NOT NULL,
    content_digest varchar(64) NOT NULL,
    inputs_snapshot_ref varchar(1024) NOT NULL,
    status_at_close varchar(40) NOT NULL,
    frozen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT pk_amendment_document_revisions PRIMARY KEY (document_id, revision_no),
    CONSTRAINT uq_amendment_revision_lifecycle UNIQUE (document_id, lifecycle_change_revision),
    CONSTRAINT chk_amendment_revision_status CHECK (status_at_close IN (
        'OPEN', 'SUPERSEDED', 'EXECUTED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'
    )),
    CONSTRAINT chk_amendment_revision_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_amendment_revision_no CHECK (revision_no >= 1)
);

CREATE UNIQUE INDEX uq_amendment_revision_one_executed
    ON amendment_document_revisions (document_id)
    WHERE status_at_close = 'EXECUTED';

ALTER TABLE amendment_documents
    ADD CONSTRAINT fk_amendment_document_current_revision
    FOREIGN KEY (id, current_revision_no)
    REFERENCES amendment_document_revisions (document_id, revision_no)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE amendment_application_units (
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    unit_no integer NOT NULL,
    tenant_id uuid NOT NULL,
    dependency_order integer NOT NULL,
    target_type varchar(40) NOT NULL,
    target_ref varchar(255) NOT NULL,
    observed_commercial_revision bigint NOT NULL,
    applied_commercial_revision bigint,
    apply_key_digest varchar(64),
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT pk_amendment_application_units PRIMARY KEY (document_id, unit_no),
    CONSTRAINT chk_amendment_unit_target CHECK (target_type IN ('SUBSCRIPTION', 'ENTITLEMENT', 'ONE_TIME', 'NONE')),
    CONSTRAINT chk_amendment_unit_status CHECK (status IN ('PENDING', 'APPLIED', 'BLOCKED')),
    CONSTRAINT chk_amendment_unit_no CHECK (unit_no >= 1),
    CONSTRAINT chk_amendment_unit_apply_digest CHECK (
        apply_key_digest IS NULL OR apply_key_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE TABLE amendment_document_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    revision_no integer NOT NULL,
    line_no integer NOT NULL,
    change_line_ref uuid NOT NULL,
    action_code varchar(40) NOT NULL,
    business_category varchar(40) NOT NULL,
    apply_target_type varchar(40) NOT NULL,
    target_subscription_id uuid,
    source_accepted_line_uuid varchar(255),
    result_accepted_line_uuid varchar(255),
    quantity_semantics varchar(40) NOT NULL,
    start_qty numeric(19, 4) NOT NULL,
    delta_qty numeric(19, 4) NOT NULL,
    end_qty numeric(19, 4) NOT NULL,
    metric varchar(64),
    effective_from date NOT NULL,
    effective_to date,
    charge_type varchar(40) NOT NULL,
    price_as_of varchar(40) NOT NULL,
    product_external_id varchar(255) NOT NULL,
    pricebook_version varchar(64) NOT NULL,
    configuration_hash varchar(64) NOT NULL,
    unit_price numeric(19, 4),
    net_amount numeric(19, 4),
    policy_slot varchar(40) NOT NULL,
    policy_id varchar(255),
    policy_version varchar(64),
    policy_decision_id varchar(255),
    pricing_trace_id varchar(255),
    segment_schedule jsonb,
    override_value numeric(19, 4),
    override_reason_code varchar(128),
    override_actor varchar(255),
    application_unit_no integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_amendment_line_triple UNIQUE (document_id, revision_no, line_no),
    CONSTRAINT fk_amendment_line_revision FOREIGN KEY (document_id, revision_no)
        REFERENCES amendment_document_revisions (document_id, revision_no),
    CONSTRAINT fk_amendment_line_unit FOREIGN KEY (document_id, application_unit_no)
        REFERENCES amendment_application_units (document_id, unit_no),
    CONSTRAINT chk_amendment_line_action CHECK (action_code IN (
        'ADD', 'UPDATE', 'REMOVE', 'NO_CHANGE', 'RENEW', 'UPGRADE', 'DOWNGRADE', 'SWAP',
        'CONVERT_METRIC', 'SUSPEND', 'RESUME', 'TERMINATE'
    )),
    CONSTRAINT chk_amendment_line_category CHECK (business_category IN (
        'INITIAL_SALE', 'RENEWAL', 'UPSELL', 'DOWNSELL', 'CROSS_SELL', 'UPGRADE', 'DOWNGRADE',
        'SWAP', 'CANCELLATION', 'TERMS_AND_CONDITIONS', 'TRANSFER', 'TRUE_UP'
    )),
    CONSTRAINT chk_amendment_line_target CHECK (apply_target_type IN ('SUBSCRIPTION', 'ENTITLEMENT', 'ONE_TIME', 'NONE')),
    CONSTRAINT chk_amendment_line_qty_semantics CHECK (quantity_semantics IN ('DELTA', 'ABSOLUTE', 'REPLACE')),
    CONSTRAINT chk_amendment_line_charge CHECK (charge_type IN ('RECURRING', 'ONE_TIME', 'USAGE')),
    CONSTRAINT chk_amendment_line_price_as_of CHECK (price_as_of IN ('ACTION_DATE', 'EFFECTIVE_DATE', 'LINE_START')),
    CONSTRAINT chk_amendment_line_policy_slot CHECK (policy_slot IN (
        'AMENDMENT', 'RENEWAL', 'PRICE_LOCK', 'TRUE_UP', 'MAINTENANCE', 'CONVERSION'
    )),
    CONSTRAINT chk_amendment_line_qty CHECK (start_qty + delta_qty = end_qty AND end_qty >= 0),
    CONSTRAINT chk_amendment_line_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_amendment_line_subscription_target CHECK (
        apply_target_type <> 'SUBSCRIPTION' OR target_subscription_id IS NOT NULL
    ),
    CONSTRAINT chk_amendment_line_source_required CHECK (
        action_code NOT IN ('UPDATE', 'REMOVE', 'SWAP', 'UPGRADE', 'DOWNGRADE')
        OR source_accepted_line_uuid IS NOT NULL
    ),
    CONSTRAINT chk_amendment_line_none_unpriced CHECK (
        apply_target_type <> 'NONE' OR (unit_price IS NULL AND net_amount IS NULL)
    ),
    CONSTRAINT chk_amendment_line_priced_policy CHECK (
        (unit_price IS NULL AND net_amount IS NULL)
        OR (policy_id IS NOT NULL AND policy_version IS NOT NULL
            AND policy_decision_id IS NOT NULL AND pricing_trace_id IS NOT NULL)
    ),
    CONSTRAINT chk_amendment_line_override CHECK (
        (override_value IS NULL AND override_reason_code IS NULL AND override_actor IS NULL)
        OR (override_value IS NOT NULL AND override_reason_code IS NOT NULL AND override_actor IS NOT NULL)
    ),
    CONSTRAINT chk_amendment_line_configuration_hash CHECK (configuration_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_amendment_line_no CHECK (line_no >= 1)
);

CREATE UNIQUE INDEX uq_amendment_line_source
    ON amendment_document_lines (document_id, revision_no, source_accepted_line_uuid)
    WHERE source_accepted_line_uuid IS NOT NULL;

CREATE TABLE amendment_document_events (
    event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL REFERENCES amendment_documents (id),
    seq_no bigint NOT NULL,
    event_type varchar(80) NOT NULL,
    from_status varchar(40),
    to_status varchar(40),
    revision_no integer,
    unit_no integer,
    actor_id varchar(255) NOT NULL,
    actor_type varchar(40) NOT NULL,
    actor_role varchar(255) NOT NULL,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    reason_code varchar(128),
    payload_digest varchar(64) NOT NULL,
    payload_ref varchar(1024) NOT NULL,
    prev_hash varchar(64) NOT NULL,
    content_hash varchar(64) NOT NULL,
    source_event_id uuid,
    correlation_id varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    CONSTRAINT uq_amendment_event_seq UNIQUE (document_id, seq_no),
    CONSTRAINT uq_amendment_event_content_hash UNIQUE (content_hash),
    CONSTRAINT chk_amendment_event_seq CHECK (seq_no >= 1),
    CONSTRAINT chk_amendment_event_actor CHECK (actor_type IN ('USER', 'SYSTEM', 'SERVICE')),
    CONSTRAINT chk_amendment_event_from_status CHECK (from_status IS NULL OR from_status IN (
        'DRAFT', 'IN_REVIEW', 'APPROVED', 'OUT_FOR_SIGNATURE', 'REJECTED', 'EXECUTED',
        'WITHDRAWN', 'APPLIED', 'PARTIALLY_APPLIED', 'APPLY_BLOCKED', 'VOID'
    )),
    CONSTRAINT chk_amendment_event_to_status CHECK (to_status IS NULL OR to_status IN (
        'DRAFT', 'IN_REVIEW', 'APPROVED', 'OUT_FOR_SIGNATURE', 'REJECTED', 'EXECUTED',
        'WITHDRAWN', 'APPLIED', 'PARTIALLY_APPLIED', 'APPLY_BLOCKED', 'VOID'
    )),
    CONSTRAINT chk_amendment_event_hashes CHECK (
        prev_hash ~ '^[0-9a-f]{64}$'
        AND content_hash ~ '^[0-9a-f]{64}$'
        AND payload_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE UNIQUE INDEX uq_amendment_event_source
    ON amendment_document_events (source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE subscriptions
    ADD COLUMN contract_id uuid REFERENCES contracts (id);

CREATE INDEX idx_subscriptions_contract_id ON subscriptions (contract_id);

ALTER TABLE subscription_rate_periods
    ADD COLUMN amendment_document_line_id uuid REFERENCES amendment_document_lines (id),
    ADD COLUMN revision_no integer,
    ADD CONSTRAINT chk_rate_period_amendment_trace CHECK (
        (amendment_document_line_id IS NULL AND revision_no IS NULL)
        OR (amendment_document_line_id IS NOT NULL AND revision_no IS NOT NULL)
    );

CREATE INDEX idx_rate_period_amendment_line
    ON subscription_rate_periods (amendment_document_line_id);

-- Append-only events: INSERT/SELECT for cam_app, and a trigger that rejects mutation for every role
-- including the table owner (owners bypass grants). Hash chaining itself is DRG-580.
CREATE OR REPLACE FUNCTION reject_amendment_document_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'amendment_document_events is append-only (op=%)', TG_OP
        USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_amendment_document_events_append_only
    BEFORE UPDATE OR DELETE ON amendment_document_events
    FOR EACH ROW
    EXECUTE FUNCTION reject_amendment_document_event_mutation();

CREATE TRIGGER trg_amendment_document_events_no_truncate
    BEFORE TRUNCATE ON amendment_document_events
    FOR EACH STATEMENT
    EXECUTE FUNCTION reject_amendment_document_event_mutation();

CREATE OR REPLACE FUNCTION stamp_amendment_document_event_recorded_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recorded_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_amendment_document_events_recorded_at
    BEFORE INSERT ON amendment_document_events
    FOR EACH ROW
    EXECUTE FUNCTION stamp_amendment_document_event_recorded_at();

-- V11 / A3 write-once columns. Status transitions stay in DRG-581; apply stays in DRG-582.
CREATE OR REPLACE FUNCTION reject_amendment_document_write_once()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.executed_revision_no IS NOT NULL
       AND NEW.executed_revision_no IS DISTINCT FROM OLD.executed_revision_no THEN
        RAISE EXCEPTION 'executed_revision_no is write-once' USING ERRCODE = '23514';
    END IF;
    IF OLD.executed_at IS NOT NULL AND NEW.executed_at IS DISTINCT FROM OLD.executed_at THEN
        RAISE EXCEPTION 'executed_at is write-once' USING ERRCODE = '23514';
    END IF;
    IF OLD.amendment_seq IS NOT NULL AND NEW.amendment_seq IS DISTINCT FROM OLD.amendment_seq THEN
        RAISE EXCEPTION 'amendment_seq is write-once' USING ERRCODE = '23514';
    END IF;
    IF OLD.amendment_number IS NOT NULL AND NEW.amendment_number IS DISTINCT FROM OLD.amendment_number THEN
        RAISE EXCEPTION 'amendment_number is write-once' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_amendment_document_write_once
    BEFORE UPDATE ON amendment_documents
    FOR EACH ROW
    EXECUTE FUNCTION reject_amendment_document_write_once();

CREATE OR REPLACE FUNCTION reject_frozen_amendment_revision_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.frozen_at IS NOT NULL THEN
        RAISE EXCEPTION 'frozen amendment revision is immutable (op=%)', TG_OP
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_amendment_revision_frozen
    BEFORE UPDATE OR DELETE ON amendment_document_revisions
    FOR EACH ROW
    EXECUTE FUNCTION reject_frozen_amendment_revision_mutation();

CREATE OR REPLACE FUNCTION reject_frozen_amendment_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    frozen boolean;
BEGIN
    EXECUTE format(
        'SELECT frozen_at IS NOT NULL FROM %I.amendment_document_revisions WHERE document_id = $1 AND revision_no = $2',
        TG_TABLE_SCHEMA)
        INTO frozen
        USING OLD.document_id, OLD.revision_no;
    IF frozen THEN
        RAISE EXCEPTION 'lines of a frozen amendment revision are immutable (op=%)', TG_OP
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_amendment_line_frozen_revision
    BEFORE UPDATE OR DELETE ON amendment_document_lines
    FOR EACH ROW
    EXECUTE FUNCTION reject_frozen_amendment_line_mutation();

REVOKE ALL ON TABLE amendment_document_events FROM PUBLIC;
GRANT SELECT, INSERT ON TABLE amendment_document_events TO cam_app;
