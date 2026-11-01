-- DRG-482 / BC-12-02: account hierarchy on the organization (account) row.
-- Nullable parent pointer with a self-FK. Self-parent is rejected here; ancestor loops are rejected
-- by AccountHierarchyService before the link is saved.

ALTER TABLE organizations
    ADD COLUMN parent_organization_id BIGINT;

ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_parent
        FOREIGN KEY (parent_organization_id) REFERENCES organizations (id);

ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_parent_not_self
        CHECK (parent_organization_id IS NULL OR parent_organization_id <> id);

CREATE INDEX idx_org_parent ON organizations (parent_organization_id);

COMMENT ON COLUMN organizations.parent_organization_id IS
    'Parent organization id. NULL is a root account. Cycles are rejected before the link is stored.';
