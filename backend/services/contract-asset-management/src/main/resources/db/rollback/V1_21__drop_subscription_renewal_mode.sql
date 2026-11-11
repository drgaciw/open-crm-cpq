-- Guarded rollback for V1_21. Not a Flyway migration.
-- Refuses while any renewal_mode is set.

DO $$
DECLARE
    modeled bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'subscriptions'
          AND column_name = 'renewal_mode'
    ) THEN
        EXECUTE 'SELECT count(*) FROM subscriptions WHERE renewal_mode IS NOT NULL' INTO modeled;
        IF modeled > 0 THEN
            RAISE EXCEPTION 'refusing to drop renewal_mode: % row(s) classified', modeled;
        END IF;
        EXECUTE 'ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS chk_subscription_renewal_mode';
        EXECUTE 'ALTER TABLE subscriptions DROP COLUMN renewal_mode';
    END IF;

    DELETE FROM flyway_schema_history WHERE version = '1.21';
END $$;
