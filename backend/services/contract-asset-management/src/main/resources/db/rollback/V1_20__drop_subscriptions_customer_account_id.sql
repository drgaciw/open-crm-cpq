-- Guarded rollback for V1_20. Not a Flyway migration.
-- Run in the CAM schema (contract unless DB_SCHEMA says otherwise).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'subscriptions'
          AND column_name = 'customer_account_id'
    ) THEN
        EXECUTE 'ALTER TABLE subscriptions DROP COLUMN customer_account_id';
    ELSE
        RAISE NOTICE 'subscriptions.customer_account_id is already absent in %', current_schema();
    END IF;
    DELETE FROM flyway_schema_history WHERE version = '1.20';
END $$;
