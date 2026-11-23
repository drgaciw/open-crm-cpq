-- DRG-514a / ADR-031 D-01. Typed custom-field registry in the crm schema.
-- field_definition, field_option and field_value are JDBC-mapped by
-- JdbcFieldRegistryStore. CustomFieldSchema holds the same DDL for tests.
-- There is no JPA entity for these tables. Names, types, nullability,
-- checks, foreign keys and indexes follow that mapping.
-- ADR-031 storage classes are separate columns (numeric is NUMERIC(24,4),
-- not JSON). Unique (definition, record) is a partial unique index per
-- entity id, which is what the store's ON CONFLICT clauses require.
-- One btree index per storage class. saved_view (D-06) is not created here.

CREATE TABLE field_definition (
    id BIGSERIAL PRIMARY KEY,
    tenant VARCHAR(128) NOT NULL,
    entity VARCHAR(32) NOT NULL,
    "key" VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL,
    show_on_record BOOLEAN NOT NULL,
    show_on_list BOOLEAN NOT NULL,
    position INTEGER NOT NULL,
    agent_filled BOOLEAN NOT NULL,
    agent_brief TEXT,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_field_definition_entity CHECK (entity IN ('organization', 'contact', 'deal')),
    CONSTRAINT ck_field_definition_type CHECK (type IN (
        'text', 'long_text', 'number', 'date', 'checkbox', 'select', 'url', 'email', 'phone', 'user')),
    CONSTRAINT ck_field_definition_key CHECK ("key" ~ '^[a-z][a-z0-9_]{0,62}$'),
    CONSTRAINT ck_field_definition_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_field_definition_position CHECK (position >= 0 AND position <= 100000),
    CONSTRAINT uq_field_definition_tenant_entity_key UNIQUE (tenant, entity, "key")
);

CREATE TABLE field_option (
    id BIGSERIAL PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    label VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_field_option_definition FOREIGN KEY (definition_id) REFERENCES field_definition (id),
    CONSTRAINT ck_field_option_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_field_option_position CHECK (position >= 0 AND position <= 100000)
);

CREATE UNIQUE INDEX uq_field_option_active_label
    ON field_option (definition_id, label)
    WHERE archived_at IS NULL;

CREATE TABLE field_value (
    id BIGSERIAL PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    organization_id BIGINT,
    contact_id BIGINT,
    deal_id BIGINT,
    "text" TEXT,
    "numeric" NUMERIC(24,4),
    "date" DATE,
    "bool" BOOLEAN,
    option_id BIGINT,
    user_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_field_value_definition FOREIGN KEY (definition_id) REFERENCES field_definition (id),
    CONSTRAINT fk_field_value_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_field_value_contact FOREIGN KEY (contact_id) REFERENCES contacts (id),
    CONSTRAINT fk_field_value_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT fk_field_value_option FOREIGN KEY (option_id) REFERENCES field_option (id),
    CONSTRAINT ck_field_value_one_record CHECK (
        (CASE WHEN organization_id IS NULL THEN 0 ELSE 1 END
        + CASE WHEN contact_id IS NULL THEN 0 ELSE 1 END
        + CASE WHEN deal_id IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT ck_field_value_one_storage CHECK (
        (CASE WHEN "text" IS NULL THEN 0 ELSE 1 END
        + CASE WHEN "numeric" IS NULL THEN 0 ELSE 1 END
        + CASE WHEN "date" IS NULL THEN 0 ELSE 1 END
        + CASE WHEN "bool" IS NULL THEN 0 ELSE 1 END
        + CASE WHEN option_id IS NULL THEN 0 ELSE 1 END
        + CASE WHEN user_id IS NULL THEN 0 ELSE 1 END) = 1
    )
);

CREATE UNIQUE INDEX uq_field_value_organization
    ON field_value (definition_id, organization_id)
    WHERE organization_id IS NOT NULL;

CREATE UNIQUE INDEX uq_field_value_contact
    ON field_value (definition_id, contact_id)
    WHERE contact_id IS NOT NULL;

CREATE UNIQUE INDEX uq_field_value_deal
    ON field_value (definition_id, deal_id)
    WHERE deal_id IS NOT NULL;

-- "text" holds up to FieldRules.LONG_TEXT_MAX (20000) characters. A btree on the
-- raw column exceeds Postgres's index row limit and the insert fails with
-- SQLSTATE 54000. left("text", 256) keeps that key bounded.
-- JdbcFieldRegistryStore.textValues filters by definition_id and
-- "text" IS NOT NULL. It does not equality-match, prefix-match, or sort the
-- text, so this index is usable for the leading definition_id column and the
-- store SQL does not reference left(). md5("text") cannot order a list, and
-- the store never compares text values.
CREATE INDEX idx_field_value_text
    ON field_value (definition_id, left("text", 256));

CREATE INDEX idx_field_value_numeric
    ON field_value (definition_id, "numeric");

CREATE INDEX idx_field_value_date
    ON field_value (definition_id, "date");

CREATE INDEX idx_field_value_bool
    ON field_value (definition_id, "bool");

CREATE INDEX idx_field_value_option
    ON field_value (definition_id, option_id);

CREATE INDEX idx_field_value_user
    ON field_value (definition_id, user_id);
