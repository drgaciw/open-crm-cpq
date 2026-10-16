-- DRG-546. First-class continuation operation so EXTENSION and RENEWAL are distinguishable
-- without parsing continuation_request_json. deal_type stays NEW | AMENDMENT | RENEWAL | EXPANSION.
-- EXTENSION and RENEWAL both remain deal_type RENEWAL; CAM accepts that set.
--
-- Rollback is manual. Flyway Community does not run undo scripts. Apply in the quote schema, in order:
--   DROP INDEX IF EXISTS idx_quotes_continuation_operation;
--   ALTER TABLE quotes DROP CONSTRAINT IF EXISTS chk_quotes_continuation_operation;
--   ALTER TABLE quotes DROP COLUMN IF EXISTS continuation_operation;
--
-- V1_12 adds a backfilled column. Dropping it is safe only if nothing reads it; otherwise restore
-- from backup. quote-service reads continuation_operation (authoring, validate, clone, and the
-- quote response). Do not drop the column while that code is deployed.
--
-- For rows this migration backfills, the operation also remains inside continuation_request_json.
-- Dropping the column does not discard that JSON copy, but a reader of the column still breaks.
-- Limit: a quote with no continuation_request_json, blank JSON, unreadable JSON, or an operation
-- other than AMENDMENT | EXPANSION | RENEWAL | EXTENSION stays NULL. Those rows were already
-- indistinguishable. A later writer that sets continuation_operation without updating
-- continuation_request_json would lose that column-only value on rollback. This change writes both.

SET search_path TO quote;

ALTER TABLE quotes ADD COLUMN continuation_operation VARCHAR(32);

ALTER TABLE quotes ADD CONSTRAINT chk_quotes_continuation_operation
    CHECK (continuation_operation IS NULL OR continuation_operation IN (
        'AMENDMENT', 'EXPANSION', 'RENEWAL', 'EXTENSION'
    ));

COMMENT ON COLUMN quotes.continuation_operation IS
    'QuoteContinuationRequest.Operation. Null when the quote is not a typed continuation. EXTENSION stays deal_type RENEWAL.';

CREATE INDEX idx_quotes_continuation_operation ON quotes (continuation_operation)
    WHERE continuation_operation IS NOT NULL;

-- Backfill from the authoring JSON. One unreadable payload must not fail the migration.
DO $$
DECLARE
    rec RECORD;
    op TEXT;
BEGIN
    FOR rec IN
        SELECT id, continuation_request_json
        FROM quotes
        WHERE continuation_request_json IS NOT NULL
          AND btrim(continuation_request_json) <> ''
    LOOP
        BEGIN
            op := rec.continuation_request_json::jsonb ->> 'operation';
        EXCEPTION WHEN OTHERS THEN
            op := NULL;
        END;
        IF op IN ('AMENDMENT', 'EXPANSION', 'RENEWAL', 'EXTENSION') THEN
            UPDATE quotes SET continuation_operation = op WHERE id = rec.id;
        END IF;
    END LOOP;
END $$;
