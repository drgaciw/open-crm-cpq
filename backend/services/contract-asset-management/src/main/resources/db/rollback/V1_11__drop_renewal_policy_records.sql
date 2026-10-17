-- Guarded rollback for V1_11. Not a Flyway migration. Refuses while any row exists.
-- Run in the CAM schema (contract unless DB_SCHEMA says otherwise).

DO $$
DECLARE
    rows bigint;
BEGIN
    IF to_regclass(current_schema() || '.renewal_policy_record') IS NULL THEN
        RAISE NOTICE 'renewal_policy_record is already absent in %', current_schema();
        RETURN;
    END IF;
    EXECUTE 'SELECT count(*) FROM renewal_policy_record' INTO rows;
    IF rows > 0 THEN
        RAISE EXCEPTION 'refusing to drop renewal_policy_record: % row(s) present', rows;
    END IF;
    EXECUTE 'DROP TABLE renewal_policy_record';
    DELETE FROM flyway_schema_history WHERE version = '1.11';
END $$;
