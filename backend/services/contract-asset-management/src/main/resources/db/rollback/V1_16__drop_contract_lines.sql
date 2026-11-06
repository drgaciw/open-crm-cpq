-- Guarded rollback for V1_16. Not a Flyway migration. Refuses while any row exists.
-- Run in the CAM schema (contract unless DB_SCHEMA says otherwise).
-- Refuses until the V1_17 rollback has removed subscriptions.contract_line_id.

DO $$
DECLARE
    rows bigint;
BEGIN
    IF to_regclass(current_schema() || '.contract_lines') IS NULL THEN
        RAISE NOTICE 'contract_lines is already absent in %', current_schema();
        RETURN;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'subscriptions'
          AND column_name = 'contract_line_id'
    ) THEN
        RAISE EXCEPTION 'refusing to drop contract_lines: subscriptions.contract_line_id still present; run the V1_17 rollback first';
    END IF;
    EXECUTE 'SELECT count(*) FROM contract_lines' INTO rows;
    IF rows > 0 THEN
        RAISE EXCEPTION 'refusing to drop contract_lines: % row(s) present', rows;
    END IF;
    EXECUTE 'DROP TRIGGER IF EXISTS trg_contract_lines_stamp_tenant ON contract_lines';
    EXECUTE 'DROP FUNCTION IF EXISTS contract_lines_stamp_tenant_from_contract()';
    EXECUTE 'DROP TABLE contract_lines';
    DELETE FROM flyway_schema_history WHERE version = '1.16';
END $$;
