-- DRG-549. Additive renewal policy records keyed by tenant plus account, agreement, or line.
-- V1_9 stays reserved for S4. V1_10 is left for S3. No existing table is altered. No rows are seeded.
-- Guarded rollback (not a Flyway version): db/rollback/V1_11__drop_renewal_policy_records.sql

CREATE TABLE renewal_policy_record (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    scope_kind varchar(40) NOT NULL,
    scope_ref uuid NOT NULL,
    policy_version integer NOT NULL,
    mode varchar(40) NOT NULL,
    basis varchar(40),
    uplift_percent_points numeric(7, 4),
    max_increase_pct numeric(8, 4),
    min_increase_pct numeric(8, 4),
    min_unit_amount numeric(19, 6),
    not_above_list boolean,
    not_below_exit_net boolean,
    price_as_of varchar(40),
    retention_marker varchar(128),
    effective_from date,
    effective_to date,
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(255) NOT NULL,
    updated_at timestamptz,
    updated_by varchar(255),
    version integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_renewal_policy_tenant_scope_version UNIQUE (tenant_id, scope_kind, scope_ref, policy_version),
    CONSTRAINT chk_renewal_policy_scope CHECK (scope_kind IN ('ACCOUNT', 'AGREEMENT', 'LINE')),
    CONSTRAINT chk_renewal_policy_mode CHECK (mode IN ('SAME', 'UPLIFT_PCT')),
    CONSTRAINT chk_renewal_policy_version CHECK (policy_version >= 1),
    CONSTRAINT chk_renewal_policy_percent CHECK (
        uplift_percent_points IS NULL
        OR (uplift_percent_points >= 0 AND uplift_percent_points <= 100)
    ),
    CONSTRAINT chk_renewal_policy_window CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_renewal_policy_status CHECK (status IN ('ACTIVE'))
);

CREATE INDEX idx_renewal_policy_tenant_scope
    ON renewal_policy_record (tenant_id, scope_kind, scope_ref);
