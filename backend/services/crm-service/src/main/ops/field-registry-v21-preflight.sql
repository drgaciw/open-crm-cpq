-- Run with psql -X --file before and after the canonical CRM V21/V22 rollout.
\set ON_ERROR_STOP on
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL search_path = pg_catalog;
SET LOCAL client_min_messages = notice;

DO $preflight$
DECLARE
    -- Signed Flyway CRC32 values for the released SQL scripts.
    v20_checksum CONSTANT integer := 20156556;
    v21_checksum CONSTANT integer := 896777634;
    retired_v21_checksum CONSTANT integer := -696508314;
    v22_checksum CONSTANT integer := -1374655337;
    registry_names CONSTANT text[] := ARRAY['field_definition', 'field_option', 'field_value'];
    registry_object_names CONSTANT text[] := registry_names || ARRAY[
        'uq_field_definition_tenant_entity_key', 'uq_field_option_active_label',
        'uq_field_value_organization', 'uq_field_value_contact', 'uq_field_value_deal',
        'idx_field_value_text', 'idx_field_value_numeric', 'idx_field_value_date',
        'idx_field_value_bool', 'idx_field_value_option', 'idx_field_value_user'];
    v20_rows integer;
    valid_v20_rows integer;
    v21_rows integer;
    valid_v21_rows integer;
    retired_v21_rows integer;
    v22_rows integer;
    valid_v22_rows integer;
    unexpected_history_rows integer;
    conflicts text;
    incomplete_tables text[];
    blank_tenants bigint;
    entity_mismatches bigint;
    storage_mismatches bigint;
    foreign_options bigint;
BEGIN
    IF NOT EXISTS (
        SELECT FROM pg_class AS c JOIN pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'crm' AND c.relname = 'flyway_schema_history' AND c.relkind = 'r'
    ) THEN
        RAISE EXCEPTION 'CRM_V21_BLOCKED_NO_V20_HISTORY'
            USING DETAIL = 'Expected the CRM Flyway history table and a successful canonical V20 entry.';
    END IF;

    SELECT count(*) FILTER (WHERE version = '20'),
           count(*) FILTER (WHERE version = '20' AND type = 'SQL' AND success
                                AND script = 'V20__audit_entries.sql' AND checksum = v20_checksum),
           count(*) FILTER (WHERE version = '21'),
           count(*) FILTER (WHERE version = '21' AND type = 'SQL' AND success
                                AND script = 'V21__field_registry.sql' AND checksum = v21_checksum),
           count(*) FILTER (WHERE version = '21' AND type = 'SQL' AND success
                                AND script = 'V21__create_field_registry.sql' AND checksum = retired_v21_checksum),
           count(*) FILTER (WHERE version = '22'),
           count(*) FILTER (WHERE version = '22' AND type = 'SQL' AND success
                                AND script = 'V22__strengthen_field_registry_integrity.sql' AND checksum = v22_checksum),
           count(*) FILTER (WHERE version IS NOT NULL
                                AND (NOT success OR version !~ '^([1-9]|1[0-9]|20|21|22)$'))
      INTO v20_rows, valid_v20_rows, v21_rows, valid_v21_rows, retired_v21_rows,
           v22_rows, valid_v22_rows, unexpected_history_rows
      FROM crm.flyway_schema_history;

    IF v20_rows <> 1 OR valid_v20_rows <> 1 THEN
        RAISE EXCEPTION 'CRM_V21_BLOCKED_NO_V20_HISTORY'
            USING DETAIL = 'Expected exactly one successful canonical V20__audit_entries.sql entry.';
    END IF;

    IF retired_v21_rows > 0 THEN
        RAISE EXCEPTION 'CRM_V21_BLOCKED_RETIRED_CANDIDATE'
            USING HINT = 'Preserve the installed history and registry data for CRM and database owner review.';
    END IF;

    IF unexpected_history_rows <> 0
            OR v21_rows > 1 OR v21_rows <> valid_v21_rows
            OR v22_rows > 1 OR v22_rows <> valid_v22_rows
            OR (v22_rows > 0 AND v21_rows = 0) THEN
        RAISE EXCEPTION 'CRM_V21_BLOCKED_HISTORY_MISMATCH'
            USING DETAIL = format('V21 entries=%s valid=%s; V22 entries=%s valid=%s; failed or unexpected versions=%s.',
                                  v21_rows, valid_v21_rows, v22_rows, valid_v22_rows, unexpected_history_rows),
                  HINT = 'Verify the signed checksums and full Flyway history against the candidate.';
    END IF;

    IF v21_rows = 0 THEN
        SELECT string_agg(object_name, ', ' ORDER BY object_name)
          INTO conflicts
          FROM (
              SELECT format('%I (relation kind %s)', c.relname, c.relkind) AS object_name
              FROM pg_class AS c JOIN pg_namespace AS n ON n.oid = c.relnamespace
              WHERE n.nspname = 'crm' AND c.relname = ANY (registry_object_names)
              UNION ALL
              SELECT format('%I (type)', t.typname)
              FROM pg_type AS t JOIN pg_namespace AS n ON n.oid = t.typnamespace
              WHERE n.nspname = 'crm' AND t.typname = ANY (registry_names) AND t.typrelid = 0
          ) AS existing_objects;
        IF conflicts IS NOT NULL THEN
            RAISE EXCEPTION 'CRM_V21_BLOCKED_OBJECT_COLLISION'
                USING DETAIL = format('CRM registry names already present without V21: %s.', conflicts);
        END IF;
        RAISE NOTICE 'CRM_V21_READY_TO_MIGRATE';
        RETURN;
    END IF;

    SELECT ARRAY(
        SELECT expected_name
        FROM unnest(registry_names) AS expected(expected_name)
        WHERE NOT EXISTS (
            SELECT FROM pg_class AS c JOIN pg_namespace AS n ON n.oid = c.relnamespace
            WHERE n.nspname = 'crm' AND c.relname = expected.expected_name AND c.relkind = 'r'
        )
        ORDER BY expected_name
    ) INTO incomplete_tables;

    IF cardinality(incomplete_tables) > 0 THEN
        RAISE EXCEPTION 'CRM_V21_BLOCKED_TABLES_INCOMPLETE'
            USING DETAIL = format('Missing or non-table CRM registry names: %s.', array_to_string(incomplete_tables, ', '));
    END IF;

    SELECT count(*) INTO blank_tenants
        FROM crm.field_definition
        WHERE length(btrim(tenant)) = 0;

    -- Mirror the generated columns and foreign keys in V22. Report aggregate counts only.
    SELECT count(*) FILTER (WHERE d.entity IS DISTINCT FROM CASE
                WHEN v.organization_id IS NOT NULL THEN 'organization'
                WHEN v.contact_id IS NOT NULL THEN 'contact'
                ELSE 'deal'
            END),
           count(*) FILTER (WHERE (CASE d.type
                WHEN 'number' THEN 'numeric'
                WHEN 'date' THEN 'date'
                WHEN 'checkbox' THEN 'bool'
                WHEN 'select' THEN 'option'
                WHEN 'user' THEN 'user'
                ELSE 'text'
            END) IS DISTINCT FROM CASE
                WHEN v."text" IS NOT NULL THEN 'text'
                WHEN v."numeric" IS NOT NULL THEN 'numeric'
                WHEN v."date" IS NOT NULL THEN 'date'
                WHEN v."bool" IS NOT NULL THEN 'bool'
                WHEN v.option_id IS NOT NULL THEN 'option'
                ELSE 'user'
            END),
           count(*) FILTER (WHERE v.option_id IS NOT NULL AND o.definition_id IS DISTINCT FROM v.definition_id)
      INTO entity_mismatches, storage_mismatches, foreign_options
      FROM crm.field_value AS v
      LEFT JOIN crm.field_definition AS d ON d.id = v.definition_id
      LEFT JOIN crm.field_option AS o ON o.id = v.option_id;

    IF blank_tenants > 0 OR entity_mismatches > 0 OR storage_mismatches > 0 OR foreign_options > 0 THEN
        RAISE EXCEPTION 'CRM_V22_BLOCKED_INVALID_REGISTRY_DATA'
            USING DETAIL = format('blank_tenants=%s entity_mismatches=%s storage_mismatches=%s foreign_options=%s',
                                  blank_tenants, entity_mismatches, storage_mismatches, foreign_options),
                  HINT = 'Route incompatible data to the CRM and database owners for review.';
    END IF;

    IF v22_rows = 0 THEN
        RAISE NOTICE 'CRM_V22_READY_TO_MIGRATE';
    ELSE
        RAISE NOTICE 'CRM_V22_ALREADY_APPLIED';
    END IF;
END;
$preflight$;

ROLLBACK;
