-- Preserve V1_7's checksum; freeze membership as well as existing line content.
CREATE OR REPLACE FUNCTION reject_frozen_amendment_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    frozen boolean;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        EXECUTE format(
            'SELECT frozen_at IS NOT NULL FROM %I.amendment_document_revisions WHERE document_id = $1 AND revision_no = $2 FOR SHARE',
            TG_TABLE_SCHEMA)
            INTO frozen USING OLD.document_id, OLD.revision_no;
        IF frozen THEN
            RAISE EXCEPTION 'lines of a frozen amendment revision are immutable (op=%)', TG_OP
                USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        -- The row lock also serializes a line write with a concurrent revision freeze.
        EXECUTE format(
            'SELECT frozen_at IS NOT NULL FROM %I.amendment_document_revisions WHERE document_id = $1 AND revision_no = $2 FOR SHARE',
            TG_TABLE_SCHEMA)
            INTO frozen USING NEW.document_id, NEW.revision_no;
        IF frozen THEN
            RAISE EXCEPTION 'lines of a frozen amendment revision are immutable (op=%)', TG_OP
                USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER trg_amendment_line_frozen_revision ON amendment_document_lines;
CREATE TRIGGER trg_amendment_line_frozen_revision
    BEFORE INSERT OR UPDATE OR DELETE ON amendment_document_lines
    FOR EACH ROW EXECUTE FUNCTION reject_frozen_amendment_line_mutation();
