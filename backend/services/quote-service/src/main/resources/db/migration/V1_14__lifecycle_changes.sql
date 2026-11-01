-- DRG-583 phase A. Expand-only LifecycleChange storage for intent AMEND.
-- Rejections are recorded in lifecycle_change_audit. The existing quote-level
-- commercial decision table cannot hold them: its quote identifier is NOT NULL
-- and points at a quote row. CPQ Decision 2026-10-26 option b. This migration
-- does not alter that table and adds no foreign key to a quote.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the quote schema, in order:
--   DROP TABLE IF EXISTS lifecycle_change_audit;
--   DROP TABLE IF EXISTS lifecycle_change_lines;
--   DROP TABLE IF EXISTS lifecycle_changes;
--
-- Dropping these tables discards stored commercial intent and rejection rows.
-- Do not drop them while quote-service still writes them.

SET search_path TO quote;

CREATE TABLE lifecycle_changes (
    id UUID PRIMARY KEY,
    tid VARCHAR(64) NOT NULL,
    intent VARCHAR(32) NOT NULL,
    initiation_trigger VARCHAR(32) NOT NULL,
    request_identifier VARCHAR(200),
    idempotency_key VARCHAR(200),
    idempotency_body_hash VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    revision INTEGER NOT NULL,
    version BIGINT NOT NULL,
    action_date DATE NOT NULL,
    contract_effective_date DATE NOT NULL,
    service_activation_date DATE,
    customer_acceptance_date DATE,
    backdating_reason_code VARCHAR(100),
    contract_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    output_mode VARCHAR(32) NOT NULL,
    content_digest VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL,
    CONSTRAINT chk_lifecycle_changes_intent CHECK (intent = 'AMEND'),
    CONSTRAINT chk_lifecycle_changes_status CHECK (status IN (
        'DRAFT', 'PRICED', 'IN_APPROVAL', 'APPROVED', 'PRESENTED', 'ACCEPTED',
        'REJECTED', 'EXPIRED', 'SUPERSEDED', 'ORDERED', 'APPLIED', 'REVERTED', 'WITHDRAWN'
    ))
);

CREATE UNIQUE INDEX uq_lifecycle_changes_idempotency
    ON lifecycle_changes (tid, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_lifecycle_changes_tid ON lifecycle_changes (tid);

CREATE TABLE lifecycle_change_lines (
    id UUID PRIMARY KEY,
    lifecycle_change_id UUID NOT NULL REFERENCES lifecycle_changes (id),
    tid VARCHAR(64) NOT NULL,
    line_no INTEGER NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    quantity_semantics VARCHAR(16) NOT NULL,
    business_category VARCHAR(40) NOT NULL,
    apply_target_type VARCHAR(32) NOT NULL,
    target_subscription_id VARCHAR(64),
    source_accepted_line_uuid VARCHAR(64),
    observed_commercial_revision BIGINT,
    start_qty NUMERIC(19, 4) NOT NULL,
    delta_qty NUMERIC(19, 4) NOT NULL,
    end_qty NUMERIC(19, 4) NOT NULL,
    metric VARCHAR(100),
    effective_from DATE NOT NULL,
    effective_to DATE,
    charge_type VARCHAR(32) NOT NULL,
    price_as_of VARCHAR(32) NOT NULL,
    policy_slot VARCHAR(32) NOT NULL,
    amend_mode VARCHAR(32),
    proration_method VARCHAR(32),
    policy_decision_id VARCHAR(64),
    policy_id VARCHAR(100),
    policy_version VARCHAR(64),
    pricing_trace_id VARCHAR(64),
    product_external_id VARCHAR(255),
    pricebook_version VARCHAR(64),
    configuration_hash VARCHAR(64),
    unit_price NUMERIC(19, 4),
    net_amount NUMERIC(19, 4),
    CONSTRAINT uq_lifecycle_change_lines_no UNIQUE (lifecycle_change_id, line_no),
    CONSTRAINT chk_lifecycle_change_lines_action CHECK (action_code IN (
        'ADD', 'UPDATE', 'REMOVE', 'SWAP', 'UPGRADE', 'DOWNGRADE'
    )),
    CONSTRAINT chk_lifecycle_change_lines_semantics CHECK (quantity_semantics IN ('DELTA', 'ABSOLUTE')),
    CONSTRAINT chk_lifecycle_change_lines_end_qty CHECK (end_qty >= 0)
);

CREATE INDEX idx_lifecycle_change_lines_change ON lifecycle_change_lines (lifecycle_change_id);

CREATE TABLE lifecycle_change_audit (
    id BIGSERIAL PRIMARY KEY,
    tid VARCHAR(64) NOT NULL,
    lifecycle_change_id UUID,
    subscription_id VARCHAR(200),
    decision_kind VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    decided_by VARCHAR(100) NOT NULL,
    inputs_json TEXT NOT NULL,
    outputs_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lifecycle_change_audit_tid ON lifecycle_change_audit (tid);
CREATE INDEX idx_lifecycle_change_audit_change ON lifecycle_change_audit (lifecycle_change_id);
