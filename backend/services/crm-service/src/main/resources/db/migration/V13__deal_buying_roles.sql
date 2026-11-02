-- DRG-518 / BC-13-06: one buying role per contact on a deal.
-- The role list is closed in the database. It is not free text and not a tenant option table.
-- Playbook stage requirements are DRG-486 and are not stored here.
-- A soft-deleted row keeps the (deal_id, contact_id) key. Assigning that pair again
-- clears is_deleted on the same row instead of inserting a second role.

CREATE TABLE deal_buying_roles (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    deal_id BIGINT NOT NULL REFERENCES deals (id),
    contact_id BIGINT NOT NULL REFERENCES contacts (id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_deal_buying_roles_deal_contact UNIQUE (deal_id, contact_id),
    CONSTRAINT ck_deal_buying_roles_role CHECK (role IN (
        'DECISION_MAKER',
        'CHAMPION',
        'ECONOMIC_BUYER',
        'BILLING_CONTACT',
        'TECHNICAL_EVALUATOR',
        'BLOCKER'
    ))
);

CREATE INDEX idx_deal_buying_roles_contact ON deal_buying_roles (contact_id);

COMMENT ON TABLE deal_buying_roles IS
    'DRG-518 buying committee. One role per contact on a deal. A contact may sit on many deals.';
COMMENT ON COLUMN deal_buying_roles.role IS
    'Closed buying role: DECISION_MAKER, CHAMPION, ECONOMIC_BUYER, BILLING_CONTACT, TECHNICAL_EVALUATOR, BLOCKER.';
