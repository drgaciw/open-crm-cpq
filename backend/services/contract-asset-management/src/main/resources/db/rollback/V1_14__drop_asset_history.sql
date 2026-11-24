-- DATA-DESTRUCTIVE: DROP TABLE asset_history permanently deletes every asset history row.
-- Exact rollback of V1_14__create_asset_history. Not a Flyway migration.
-- Run this before V1_13__drop_assets.sql.
-- Set search_path to the CAM schema first (contract unless DB_SCHEMA says otherwise).
-- This script does not change flyway_schema_history. Repair does not forget a
-- successful 1.14. A throwaway re-apply deletes that history row and migrates
-- out of order, because later versions stay applied. clean() would drop the schema.

DROP INDEX IF EXISTS idx_asset_history_successor;
DROP INDEX IF EXISTS idx_asset_history_predecessor;
DROP INDEX IF EXISTS idx_asset_history_type;
DROP INDEX IF EXISTS idx_asset_history_account;
DROP INDEX IF EXISTS idx_asset_history_asset;

ALTER TABLE IF EXISTS asset_history DROP CONSTRAINT IF EXISTS chk_asset_history_type;
ALTER TABLE IF EXISTS asset_history DROP CONSTRAINT IF EXISTS uq_asset_history_event;
ALTER TABLE IF EXISTS asset_history DROP CONSTRAINT IF EXISTS asset_history_pkey;

DROP TABLE IF EXISTS asset_history;
