-- V2/V3 persisted DRL bodies without the imports present in the resource corpus.
-- Add only the namespace/header; preserve each seed's complete logic and policy metadata.
-- Match the exact managed body (line-ending normalization only), so owner-edited DRL is untouched.
SET search_path TO pricing;

UPDATE pricing.rules rule
SET content = E'package com.opencrm.pricing.rules;\nimport com.opencrm.rules.model.QuoteFact;\nimport com.opencrm.rules.model.QuoteLineItemFact;\nimport com.opencrm.rules.model.CustomerFact;\n' || rule.content
FROM pricing.rule_sets rule_set,
(VALUES
    ('rule-001', 'bfae16feb1d9b4fa3b02be7fc05a7d32'),
    ('rule-002', '6c0be02c57034d5ee338176728a9e010'),
    ('rule-003', 'bb4cb5d8048705488fde498e7ff608aa'),
    ('rule-004', '12591b6addbc0ced517cde645181f1be'),
    ('rule-005', 'b3f8d59c992a9bb1dfb8b38d8c6e81ba'),
    ('rule-006', 'd0b5275a07b28f18a3b678c44a4254db'),
    ('rule-007', '09d476c40a621e7ea6c565a14488f4d4'),
    ('rule-008', 'e40d373b37f5619701e6574cb5543336'),
    ('rule-009', '9fde44df9dadba79e937aead52711ba1'),
    ('rule-010', '39b229f2b5cc7fec2d061c2eb59d9091'),
    ('rule-011', 'da3e75f0b3e294f5ce2cd5078a56451c')
) AS managed(uuid, original_body_md5)
WHERE rule.rule_set_id = rule_set.id
  AND rule_set.uuid = 'rs-default-001'
  AND rule.uuid = managed.uuid
  AND rule.created_by = 'system'
  AND md5(replace(rule.content, E'\r\n', E'\n')) = managed.original_body_md5;
