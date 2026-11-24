-- Guarded rollback for V1_13. Not a Flyway migration.
-- Refuses while a matrix version or a routed approval request exists.

DO $$
DECLARE
    matrices bigint;
    routed bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'approval_matrices'
    ) THEN
        EXECUTE 'SELECT count(*) FROM approval_matrices' INTO matrices;
        IF matrices > 0 THEN
            RAISE EXCEPTION 'refusing to drop approval matrices: % row(s) exist', matrices;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'approval_requests'
          AND column_name = 'matrix_id'
    ) THEN
        EXECUTE 'SELECT count(*) FROM approval_requests WHERE matrix_id IS NOT NULL' INTO routed;
        IF routed > 0 THEN
            RAISE EXCEPTION 'refusing to drop matrix columns: % routed request(s) exist', routed;
        END IF;
        EXECUTE 'ALTER TABLE approval_requests DROP CONSTRAINT IF EXISTS fk_approval_request_matrix';
        EXECUTE 'ALTER TABLE approval_requests DROP CONSTRAINT IF EXISTS chk_approval_matrix_step';
        EXECUTE 'DROP INDEX IF EXISTS idx_approval_tenant_status';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS tenant_id';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS matrix_id';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS matrix_version';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS quote_version';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS routing_step';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS step_mode';
        EXECUTE 'ALTER TABLE approval_requests DROP COLUMN IF EXISTS breach_types';
    END IF;

    EXECUTE 'DROP TABLE IF EXISTS approval_matrix_rules';
    EXECUTE 'DROP TABLE IF EXISTS approval_matrices';
    DELETE FROM flyway_schema_history WHERE version = '1.13';
END $$;
