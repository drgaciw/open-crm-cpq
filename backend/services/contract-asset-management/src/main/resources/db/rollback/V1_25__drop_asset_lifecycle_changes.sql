-- DRG-902 (902-A) rollback for V1_25__create_asset_lifecycle_changes.sql.
-- Dropping the table also drops asset_lifecycle_change_seq because the sequence is owned
-- by sequence_no. The second statement covers a sequence that was created but not yet owned.

DROP TABLE IF EXISTS asset_lifecycle_changes;
DROP SEQUENCE IF EXISTS asset_lifecycle_change_seq;
