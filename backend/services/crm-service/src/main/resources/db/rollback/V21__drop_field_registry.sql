-- DATA-DESTRUCTIVE: DROP TABLE field_value, field_option and field_definition
-- permanently deletes every custom-field value, option and definition.
-- Exact rollback of V21__field_registry. Not a Flyway migration.
-- db/rollback is outside classpath:db/migration.
-- Drops indexes and constraints first, then field_value, field_option and
-- field_definition. Does not drop saved_view or any object from V1 through V20.
-- Run in the crm schema. This script does not change flyway_schema_history.
-- Flyway repair does not forget a successful V21, so a throwaway re-apply
-- deletes only that history row. Do not use clean: it drops V1 through V20 too.

SET search_path TO crm;

DROP INDEX IF EXISTS idx_field_value_user;
DROP INDEX IF EXISTS idx_field_value_option;
DROP INDEX IF EXISTS idx_field_value_bool;
DROP INDEX IF EXISTS idx_field_value_date;
DROP INDEX IF EXISTS idx_field_value_numeric;
DROP INDEX IF EXISTS idx_field_value_text;
DROP INDEX IF EXISTS uq_field_value_deal;
DROP INDEX IF EXISTS uq_field_value_contact;
DROP INDEX IF EXISTS uq_field_value_organization;

ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS ck_field_value_one_storage;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS ck_field_value_one_record;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS fk_field_value_option;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS fk_field_value_deal;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS fk_field_value_contact;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS fk_field_value_organization;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS fk_field_value_definition;
ALTER TABLE IF EXISTS field_value DROP CONSTRAINT IF EXISTS field_value_pkey;

DROP INDEX IF EXISTS uq_field_option_active_label;

ALTER TABLE IF EXISTS field_option DROP CONSTRAINT IF EXISTS ck_field_option_position;
ALTER TABLE IF EXISTS field_option DROP CONSTRAINT IF EXISTS ck_field_option_label;
ALTER TABLE IF EXISTS field_option DROP CONSTRAINT IF EXISTS fk_field_option_definition;
ALTER TABLE IF EXISTS field_option DROP CONSTRAINT IF EXISTS field_option_pkey;

ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS uq_field_definition_tenant_entity_key;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS ck_field_definition_position;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS ck_field_definition_label;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS ck_field_definition_key;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS ck_field_definition_type;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS ck_field_definition_entity;
ALTER TABLE IF EXISTS field_definition DROP CONSTRAINT IF EXISTS field_definition_pkey;

DROP TABLE IF EXISTS field_value;
DROP TABLE IF EXISTS field_option;
DROP TABLE IF EXISTS field_definition;
