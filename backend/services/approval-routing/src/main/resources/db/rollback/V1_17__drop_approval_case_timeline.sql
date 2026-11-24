-- Guarded rollback for V1_17. Not a Flyway migration.
-- Drops the DRG-446 case comments and approval-history timeline.

DROP TABLE IF EXISTS approval_timeline_entries;
DROP TABLE IF EXISTS approval_case_comments;

DELETE FROM flyway_schema_history WHERE version = '1.13';
