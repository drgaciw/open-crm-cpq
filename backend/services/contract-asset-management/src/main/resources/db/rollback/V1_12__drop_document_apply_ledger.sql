-- Guarded rollback for V1_12. Not a Flyway migration. Refuses while any row exists.
-- Run in the CAM schema (contract unless DB_SCHEMA says otherwise).

DO $$
DECLARE
    rows bigint;
BEGIN
    IF to_regclass(current_schema() || '.document_apply_ledger') IS NULL THEN
        RAISE NOTICE 'document_apply_ledger is already absent in %', current_schema();
        RETURN;
    END IF;
    EXECUTE 'SELECT count(*) FROM document_apply_ledger' INTO rows;
    IF rows > 0 THEN
        RAISE EXCEPTION 'refusing to drop document_apply_ledger: % row(s) present', rows;
    END IF;
    EXECUTE 'DROP TABLE document_apply_ledger';
    DELETE FROM flyway_schema_history WHERE version = '1.12';
END $$;
