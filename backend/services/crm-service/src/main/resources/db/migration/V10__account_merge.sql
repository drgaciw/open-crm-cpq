-- DRG-484 / BC-12-04: auditable account merge.
-- The losing organization is closed with organizations.is_deleted. This migration
-- does not add columns to organizations.
-- Quote, contract, and any other service database are not updated here. Those
-- services repoint their own references when they consume AccountsMerged.

CREATE TABLE account_merges (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    survivor_organization_id BIGINT NOT NULL REFERENCES organizations(id),
    merged_away_organization_id BIGINT NOT NULL REFERENCES organizations(id),
    decided_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_account_merges_distinct CHECK (survivor_organization_id <> merged_away_organization_id),
    CONSTRAINT uq_account_merges_merged_away UNIQUE (merged_away_organization_id)
);

CREATE INDEX idx_account_merges_survivor ON account_merges (survivor_organization_id);

COMMENT ON TABLE account_merges IS 'DRG-484 audit of an organization merge. One row per merged-away organization.';
COMMENT ON COLUMN account_merges.decided_by IS 'Actor who chose the survivor (user:<oid> or service:<name>). Not taken from the request body.';
