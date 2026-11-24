package com.opencrm.crm.fields;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Typed custom-field DDL. This is not a Flyway migration.
 * <p>
 * CRM {@code V16} is owned by the unmerged DRG-714 migration
 * {@code V16__account_hierarchy_audit.sql}. The next version is not free until that
 * migration is owner-merged and main is refreshed, so this slice does not reserve
 * {@code V17} or edit {@code V16}. Production startup does not apply these statements.
 * Tests apply them on their own database.
 */
public final class CustomFieldSchema {

    static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS crm.field_definition (
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
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS crm.field_option (
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
                CONSTRAINT fk_field_option_definition FOREIGN KEY (definition_id) REFERENCES crm.field_definition (id),
                CONSTRAINT ck_field_option_label CHECK (length(btrim(label)) > 0),
                CONSTRAINT ck_field_option_position CHECK (position >= 0 AND position <= 100000)
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_field_option_active_label
                ON crm.field_option (definition_id, label)
                WHERE archived_at IS NULL
            """,
            """
            CREATE TABLE IF NOT EXISTS crm.field_value (
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
                CONSTRAINT fk_field_value_definition FOREIGN KEY (definition_id) REFERENCES crm.field_definition (id),
                CONSTRAINT fk_field_value_organization FOREIGN KEY (organization_id) REFERENCES crm.organizations (id),
                CONSTRAINT fk_field_value_contact FOREIGN KEY (contact_id) REFERENCES crm.contacts (id),
                CONSTRAINT fk_field_value_deal FOREIGN KEY (deal_id) REFERENCES crm.deals (id),
                CONSTRAINT fk_field_value_option FOREIGN KEY (option_id) REFERENCES crm.field_option (id),
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
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_field_value_organization
                ON crm.field_value (definition_id, organization_id)
                WHERE organization_id IS NOT NULL
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_field_value_contact
                ON crm.field_value (definition_id, contact_id)
                WHERE contact_id IS NOT NULL
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_field_value_deal
                ON crm.field_value (definition_id, deal_id)
                WHERE deal_id IS NOT NULL
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_text
                ON crm.field_value (definition_id, "text")
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_numeric
                ON crm.field_value (definition_id, "numeric")
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_date
                ON crm.field_value (definition_id, "date")
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_bool
                ON crm.field_value (definition_id, "bool")
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_option
                ON crm.field_value (definition_id, option_id)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_field_value_user
                ON crm.field_value (definition_id, user_id)
            """
    );

    private CustomFieldSchema() {
    }

    public static void apply(JdbcTemplate jdbc) {
        for (String statement : STATEMENTS) {
            jdbc.execute(statement);
        }
    }
}
