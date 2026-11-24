-- DATA-DESTRUCTIVE: DROP TABLE assets permanently deletes every asset row.
-- Exact rollback of V1_13__create_assets. Not a Flyway migration.
-- Run V1_14__drop_asset_history.sql before this script.
-- Set search_path to the CAM schema first (contract unless DB_SCHEMA says otherwise).
-- This script does not change flyway_schema_history. Repair does not forget a
-- successful 1.13. A throwaway re-apply deletes that history row and migrates
-- out of order, because later versions stay applied. clean() would drop the schema.

DROP INDEX IF EXISTS idx_asset_subscription;
DROP INDEX IF EXISTS idx_asset_contract_line;
DROP INDEX IF EXISTS idx_asset_account;

ALTER TABLE IF EXISTS assets DROP CONSTRAINT IF EXISTS chk_asset_status;
ALTER TABLE IF EXISTS assets DROP CONSTRAINT IF EXISTS uq_asset_subscription_contract_line;
ALTER TABLE IF EXISTS assets DROP CONSTRAINT IF EXISTS assets_subscription_id_fkey;
ALTER TABLE IF EXISTS assets DROP CONSTRAINT IF EXISTS assets_uuid_key;
ALTER TABLE IF EXISTS assets DROP CONSTRAINT IF EXISTS assets_pkey;

DROP TABLE IF EXISTS assets;
