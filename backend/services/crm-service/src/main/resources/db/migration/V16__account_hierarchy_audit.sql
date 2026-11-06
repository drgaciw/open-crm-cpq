-- DRG-714: auditable account hierarchy link / re-link / unlink.
-- Create-table only. organizations.parent_organization_id (V8) is unchanged.
-- HierarchyChanged outbox payload is unchanged.

CREATE TABLE account_hierarchy_changes (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    child_organization_id BIGINT NOT NULL REFERENCES organizations(id),
    child_account_id VARCHAR(36) NOT NULL,
    previous_parent_organization_id BIGINT REFERENCES organizations(id),
    new_parent_organization_id BIGINT REFERENCES organizations(id),
    decided_by VARCHAR(255) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_account_hierarchy_changes_child ON account_hierarchy_changes (child_organization_id);

COMMENT ON TABLE account_hierarchy_changes IS
    'DRG-714 audit of an organization parent link change. One row per successful link, re-link, or unlink.';
COMMENT ON COLUMN account_hierarchy_changes.decided_by IS
    'Actor who chose the parent (user:<oid> or service:<name>). Same source pattern as account_merges.decided_by.';
COMMENT ON COLUMN account_hierarchy_changes.new_parent_organization_id IS
    'New parent organization id. NULL means unlink (root).';
COMMENT ON COLUMN account_hierarchy_changes.decided_at IS
    'Wall-clock time of the hierarchy decision in the same transaction as the parent write.';
