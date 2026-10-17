-- DRG-542: retire seeded full-term amendment proration (rule-007).
-- QuoteContinuationPolicy is the only proration formula. Execute loads ACTIVE rules only,
-- so DISABLED removes this row from pricing. Match the unedited system seed only
-- (same owner-edit guard V7 and V8 use). V2, V7, and V8 stay as applied history.
--
-- The guarded body hash is md5 of the V8-updated system seed with newlines normalized
-- (package header from V7 plus the V8 when-clause guard). An owner-edited body or a
-- row whose created_by is not 'system' is left unchanged and a notice is raised.
--
-- Rollback: set status back to ACTIVE with the same predicate
-- (rule set rs-default-001, uuid rule-007, created_by = 'system',
-- md5(replace(content, CR LF, LF)) = 'bb7c9386a9651b979a20af49ba3cc514').
-- That statement cannot restore an owner-edited body or a non-system created_by,
-- because those rows are not updated here. It cannot recreate the deleted classpath
-- file rules/amendment-proration.drl. It also cannot restore a prior status other
-- than ACTIVE, because this migration does not record the previous status.
SET search_path TO pricing;

DO $$
DECLARE
    updated_count integer;
    already_disabled integer;
BEGIN
    UPDATE pricing.rules AS rule
    SET status = 'DISABLED'
    FROM pricing.rule_sets AS rule_set
    WHERE rule.rule_set_id = rule_set.id
      AND rule_set.uuid = 'rs-default-001'
      AND rule.uuid = 'rule-007'
      AND rule.created_by = 'system'
      AND md5(replace(rule.content, E'\r\n', E'\n')) = 'bb7c9386a9651b979a20af49ba3cc514'
      AND rule.status IS DISTINCT FROM 'DISABLED';

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    IF updated_count = 0 THEN
        SELECT count(*) INTO already_disabled
        FROM pricing.rules AS rule
        JOIN pricing.rule_sets AS rule_set ON rule.rule_set_id = rule_set.id
        WHERE rule_set.uuid = 'rs-default-001'
          AND rule.uuid = 'rule-007'
          AND rule.created_by = 'system'
          AND md5(replace(rule.content, E'\r\n', E'\n')) = 'bb7c9386a9651b979a20af49ba3cc514'
          AND rule.status = 'DISABLED';

        IF already_disabled = 0 THEN
            RAISE NOTICE 'V9 rule-007 disable no-op: created_by is not system or the body is not the unedited seed';
        END IF;
    END IF;
END $$;
