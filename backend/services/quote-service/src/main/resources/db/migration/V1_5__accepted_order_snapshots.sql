ALTER TABLE quotes ADD COLUMN accepted_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE quotes ADD COLUMN fulfillment_owner VARCHAR(16) NOT NULL DEFAULT 'LEGACY'
    CHECK (fulfillment_owner IN ('LEGACY','OMS'));
CREATE TABLE accepted_order_snapshots (
    id BIGSERIAL PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE,
    snapshot_id VARCHAR(36) NOT NULL UNIQUE, quote_id VARCHAR(36) NOT NULL,
    quote_number VARCHAR(50) NOT NULL, accepted_revision INTEGER NOT NULL CHECK (accepted_revision > 0),
    scope_id VARCHAR(255) NOT NULL, customer_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL, snapshot_hash VARCHAR(64) NOT NULL, holds TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ,
    created_by VARCHAR(255), updated_by VARCHAR(255), version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (quote_id, accepted_revision), UNIQUE (quote_number, accepted_revision)
);
CREATE FUNCTION forbid_accepted_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Accepted snapshots are immutable';
END;
$$;
CREATE TRIGGER accepted_snapshot_immutable BEFORE UPDATE OR DELETE ON accepted_order_snapshots
    FOR EACH ROW EXECUTE FUNCTION forbid_accepted_snapshot_mutation();
ALTER TABLE quotes ADD COLUMN oms_approval_reference VARCHAR(36);
