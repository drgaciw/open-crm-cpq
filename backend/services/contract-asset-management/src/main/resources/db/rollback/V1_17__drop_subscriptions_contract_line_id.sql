-- Guarded rollback for V1_17. Not a Flyway migration. Refuses while any subscription is linked
-- to a contract line, so the canonical origin link is never dropped silently.
-- Run in the CAM schema (contract unless DB_SCHEMA says otherwise).
-- Run this before the V1_16 rollback.

DO $$
DECLARE
    linked bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'subscriptions'
          AND column_name = 'contract_line_id'
    ) THEN
        EXECUTE 'SELECT count(*) FROM subscriptions WHERE contract_line_id IS NOT NULL' INTO linked;
        IF linked > 0 THEN
            RAISE EXCEPTION 'refusing to drop subscriptions.contract_line_id: % linked row(s) present', linked;
        END IF;
        EXECUTE 'ALTER TABLE subscriptions DROP COLUMN contract_line_id';
    ELSE
        RAISE NOTICE 'subscriptions.contract_line_id is already absent in %', current_schema();
    END IF;
    DELETE FROM flyway_schema_history WHERE version = '1.17';
END $$;
