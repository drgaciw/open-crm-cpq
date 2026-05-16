SET search_path TO pricing;

ALTER TABLE rules
    ADD COLUMN IF NOT EXISTS engine_type VARCHAR(30);

COMMENT ON COLUMN rules.engine_type IS
    'OPENL_TABLETS or DROOLS; null inherits rule_sets.engine_type';
