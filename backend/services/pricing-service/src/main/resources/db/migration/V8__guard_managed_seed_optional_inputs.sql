-- Guard absent optional inputs in exact V7-managed seed bodies only.
-- Nonempty malformed numeric values still reach the original typed getters and fail.
-- Preserve all actions, thresholds, metadata, and owner-edited DRL. Applied V7 is unchanged.
SET search_path TO pricing;

UPDATE pricing.rules rule
SET content = replace(rule.content, '    eval($quote.getInt("includedUnits") < $quote.getInt("actualUnits"))', '    eval($quote.getString("includedUnits") != null
         && $quote.getString("actualUnits") != null
         && $quote.getString("overageRate") != null
         && $quote.getInt("includedUnits") < $quote.getInt("actualUnits"))')
FROM pricing.rule_sets rule_set
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.created_by = 'system'
  AND rule.uuid = 'rule-002'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = 'b47892b0e6c3ff475900d2b86027a85a';

UPDATE pricing.rules rule
SET content = replace(rule.content, '    eval($qli.getInt("coreCount") > 0)', '    eval($qli.getString("coreCount") != null && $qli.getInt("coreCount") > 0)')
FROM pricing.rule_sets rule_set
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.created_by = 'system'
  AND rule.uuid = 'rule-004'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = '070d3f33a82cc99851d90547a4ad1856';

UPDATE pricing.rules rule
SET content = replace(rule.content, '    eval($quote.getBoolean("hasPerpetualEntitlement") && $quote.getBoolean("migrationProgramActive"))', '    eval(Boolean.TRUE.equals($quote.getBoolean("hasPerpetualEntitlement"))
         && Boolean.TRUE.equals($quote.getBoolean("migrationProgramActive"))
         && $quote.getString("perpetualBookValue") != null)')
FROM pricing.rule_sets rule_set
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.created_by = 'system'
  AND rule.uuid = 'rule-005'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = '7faaa9949bdb2ddd90e80355f18800eb';

UPDATE pricing.rules rule
SET content = replace(rule.content, '    eval($quote.getDecimal("estimatedMarginPct").compareTo(new java.math.BigDecimal("0.35")) < 0)', '    eval($quote.getString("estimatedMarginPct") != null
         && $quote.getDecimal("estimatedMarginPct").compareTo(new java.math.BigDecimal("0.35")) < 0)')
FROM pricing.rule_sets rule_set
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.created_by = 'system'
  AND rule.uuid = 'rule-006'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = '5bedff0416e2eb75fb2b7c4a1b96094e';

UPDATE pricing.rules rule
SET content = replace(rule.content, '    eval("AMENDMENT".equals($quote.getString("dealType")))', '    eval("AMENDMENT".equals($quote.getString("dealType"))
         && $quote.getString("remainingTermDays") != null
         && $quote.getString("fullTermDays") != null
         && $quote.getString("amendmentDeltaAmount") != null
         && $quote.getInt("fullTermDays") > 0)')
FROM pricing.rule_sets rule_set
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.created_by = 'system'
  AND rule.uuid = 'rule-007'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = 'cef36d67223e22a8a98bab47ae031b39';
