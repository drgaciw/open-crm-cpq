-- Keep the published V21 history intact. Existing rows must satisfy the new constraints.
ALTER TABLE crm.field_definition
    ADD COLUMN storage_class VARCHAR(16) GENERATED ALWAYS AS (
        CASE type
            WHEN 'number' THEN 'numeric'
            WHEN 'date' THEN 'date'
            WHEN 'checkbox' THEN 'bool'
            WHEN 'select' THEN 'option'
            WHEN 'user' THEN 'user'
            ELSE 'text'
        END
    ) STORED NOT NULL,
    ADD CONSTRAINT ck_field_definition_tenant CHECK (length(btrim(tenant)) > 0),
    ADD CONSTRAINT uq_field_definition_entity_storage UNIQUE (id, entity, storage_class);

ALTER TABLE crm.field_option
    ADD CONSTRAINT uq_field_option_definition_id UNIQUE (definition_id, id);

ALTER TABLE crm.field_value
    ADD COLUMN record_entity VARCHAR(32) GENERATED ALWAYS AS (
        CASE
            WHEN organization_id IS NOT NULL THEN 'organization'
            WHEN contact_id IS NOT NULL THEN 'contact'
            ELSE 'deal'
        END
    ) STORED NOT NULL,
    ADD COLUMN storage_class VARCHAR(16) GENERATED ALWAYS AS (
        CASE
            WHEN "text" IS NOT NULL THEN 'text'
            WHEN "numeric" IS NOT NULL THEN 'numeric'
            WHEN "date" IS NOT NULL THEN 'date'
            WHEN "bool" IS NOT NULL THEN 'bool'
            WHEN option_id IS NOT NULL THEN 'option'
            ELSE 'user'
        END
    ) STORED NOT NULL,
    DROP CONSTRAINT fk_field_value_definition,
    DROP CONSTRAINT fk_field_value_option,
    ADD CONSTRAINT fk_field_value_definition FOREIGN KEY (definition_id, record_entity, storage_class)
        REFERENCES crm.field_definition (id, entity, storage_class),
    ADD CONSTRAINT fk_field_value_option FOREIGN KEY (definition_id, option_id)
        REFERENCES crm.field_option (definition_id, id);
