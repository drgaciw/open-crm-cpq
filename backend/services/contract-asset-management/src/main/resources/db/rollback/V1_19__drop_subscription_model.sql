-- Guarded rollback for V1_19. Not a Flyway migration.
-- Refuses while any subscription_model is set or any ingest audit row exists.

DO $$
DECLARE
    modeled bigint;
    audits bigint;
BEGIN
    IF to_regclass(current_schema() || '.subscription_ingest_audits') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM subscription_ingest_audits' INTO audits;
        IF audits > 0 THEN
            RAISE EXCEPTION 'refusing to drop subscription_ingest_audits: % row(s) present', audits;
        END IF;
        EXECUTE 'DROP TABLE subscription_ingest_audits';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'subscriptions'
          AND column_name = 'subscription_model'
    ) THEN
        EXECUTE 'SELECT count(*) FROM subscriptions WHERE subscription_model IS NOT NULL' INTO modeled;
        IF modeled > 0 THEN
            RAISE EXCEPTION 'refusing to drop subscription_model: % row(s) classified', modeled;
        END IF;
        EXECUTE 'ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS chk_subscription_model';
        EXECUTE 'ALTER TABLE subscriptions DROP COLUMN subscription_model';
    END IF;

    DELETE FROM flyway_schema_history WHERE version = '1.19';
END $$;
