SET search_path TO pricing;

-- Default Rule Set: Core Pricing Rules
INSERT INTO rule_sets (uuid, name, description, status, rule_set_version, effective_date, engine_type, created_at, created_by)
VALUES (
    'rs-default-001',
    'Core Pricing Rules',
    'Default pricing rules covering volume discounts, usage overage, perpetual maintenance, core licensing, migration credits, approval routing, amendment proration, and renewal uplift.',
    'ACTIVE',
    1,
    '2026-01-01',
    'DROOLS',
    NOW(),
    'system'
);

-- Rule A: Named Seat Volume Discount (SaaS)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-001', id, 'Named Seat Volume Discount',
    'Applies tiered volume discount for named seat SaaS subscriptions based on quantity and minimum 12-month term.',
    'PRICING', 620, 'ACTIVE',
    'rule "Named Seat Volume Discount"
salience 620
when
    $qli: QuoteLineItemFact(lineItemType == "SAAS_SEAT_NAMED", $qty: quantity, $list: listPrice)
    $quote: QuoteFact(this == $qli.quote, durationInMonths >= 12)
then
    java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
    if ($qty >= 1000) discount = new java.math.BigDecimal("0.18");
    else if ($qty >= 500) discount = new java.math.BigDecimal("0.12");
    else if ($qty >= 100) discount = new java.math.BigDecimal("0.07");
    $qli.setDiscountRate(discount);
    $qli.setFinalPrice($list.multiply(java.math.BigDecimal.ONE.subtract(discount)));
end',
    '["PRODUCT_TYPE", "QUANTITY", "TERM_LENGTH"]',
    '["APPLY_DISCOUNT"]',
    'DISC_VOLUME_NAMED_SEAT',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule B: Usage Overage Charge (SaaS)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-002', id, 'Usage Overage Charge',
    'Computes overage charges when actual usage exceeds included units.',
    'PRICING', 610, 'ACTIVE',
    'rule "Usage Overage Charge"
salience 610
when
    $quote: QuoteFact()
    eval($quote.getInt("includedUnits") < $quote.getInt("actualUnits"))
then
    int includedUnits = $quote.getInt("includedUnits");
    int actualUnits = $quote.getInt("actualUnits");
    int overageUnits = actualUnits - includedUnits;
    java.math.BigDecimal overageRate = $quote.getDecimal("overageRate");
    java.math.BigDecimal overageAmount = overageRate.multiply(java.math.BigDecimal.valueOf(overageUnits));
    $quote.setDecimal("overageAmount", overageAmount);
    $quote.setString("explanationCode", "USAGE_OVERAGE_APPLIED");
end',
    '["USAGE", "INCLUDED_UNITS"]',
    '["COMPUTE_OVERAGE"]',
    'USAGE_OVERAGE_APPLIED',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule C: Perpetual Maintenance Price (On-Prem)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-003', id, 'Perpetual Maintenance Price',
    'Calculates 22% annual maintenance fee for perpetual on-prem licenses.',
    'PRICING', 580, 'ACTIVE',
    'rule "Perpetual Maintenance Price"
salience 580
when
    $qli: QuoteLineItemFact(lineItemType == "ONPREM_PERPETUAL", $licensePrice: listPrice)
then
    java.math.BigDecimal maintenancePct = new java.math.BigDecimal("0.22");
    java.math.BigDecimal maintenanceAmount = $licensePrice.multiply(maintenancePct);
    $qli.setDecimal("maintenanceAmount", maintenanceAmount);
    $qli.setString("maintenanceTier", "STANDARD");
end',
    '["PRODUCT_TYPE"]',
    '["SET_MAINTENANCE"]',
    'MAINTENANCE_STANDARD_22PCT',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule D: Core License Pack Floor (On-Prem)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-004', id, 'Core License Pack Floor',
    'Rounds up core count to minimum 16-core packs for on-prem core licensing.',
    'PRICING', 590, 'ACTIVE',
    'rule "Core License Pack Floor"
salience 590
when
    $qli: QuoteLineItemFact(lineItemType == "ONPREM_CORE")
    eval($qli.getInt("coreCount") > 0)
then
    int coreCount = $qli.getInt("coreCount");
    int minPack = 16;
    int billableCores = ((coreCount + minPack - 1) / minPack) * minPack;
    $qli.setInt("billableCores", billableCores);
    $qli.setString("explanationCode", "CORE_PACK_ROUND_UP");
end',
    '["PRODUCT_TYPE", "CORE_COUNT"]',
    '["ROUND_UP_CORES"]',
    'CORE_PACK_ROUND_UP',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule E: Perpetual to SaaS Migration Credit (Hybrid)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-005', id, 'Perpetual to SaaS Migration Credit',
    'Applies 30% migration credit on perpetual book value when migrating to SaaS.',
    'PRICING', 560, 'ACTIVE',
    'rule "Perpetual to SaaS Migration Credit"
salience 560
when
    $quote: QuoteFact()
    eval($quote.getBoolean("hasPerpetualEntitlement") && $quote.getBoolean("migrationProgramActive"))
then
    java.math.BigDecimal credit = $quote.getDecimal("perpetualBookValue")
        .multiply(new java.math.BigDecimal("0.30"));
    $quote.setDecimal("migrationCredit", credit);
    $quote.setString("explanationCode", "MIGRATION_CREDIT_30");
end',
    '["ENTITLEMENT", "MIGRATION_PROGRAM"]',
    '["APPLY_CREDIT"]',
    'MIGRATION_CREDIT_30',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule F: Route Approval for Margin Exception
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-006', id, 'Route Approval for Margin Exception',
    'Routes quote to Deal Desk approval when estimated margin falls below 35% floor.',
    'APPROVAL', 930, 'ACTIVE',
    'rule "Route Approval for Margin Exception"
salience 930
when
    $quote: QuoteFact(finalPrice != null, basePrice != null)
    eval($quote.getDecimal("estimatedMarginPct").compareTo(new java.math.BigDecimal("0.35")) < 0)
then
    $quote.setString("approvalState", "PENDING");
    $quote.setString("approvalTier", "DEAL_DESK");
    $quote.setString("explanationCode", "MARGIN_BELOW_FLOOR");
end',
    '["MARGIN"]',
    '["ROUTE_APPROVAL"]',
    'MARGIN_BELOW_FLOOR',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule G: Amendment Proration
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-007', id, 'Amendment Proration',
    'Prorates amendment delta amount based on remaining term days and co-terms to master contract.',
    'PRICING', 540, 'ACTIVE',
    'rule "Amendment Proration"
salience 540
when
    $quote: QuoteFact()
    eval("AMENDMENT".equals($quote.getString("dealType")))
then
    int remainingDays = $quote.getInt("remainingTermDays");
    int fullTermDays = $quote.getInt("fullTermDays");
    java.math.BigDecimal deltaAmount = $quote.getDecimal("amendmentDeltaAmount");
    java.math.BigDecimal prorated = deltaAmount
        .multiply(java.math.BigDecimal.valueOf(remainingDays))
        .divide(java.math.BigDecimal.valueOf(fullTermDays), 4, java.math.RoundingMode.HALF_UP);
    $quote.setDecimal("prorationAdjustment", prorated);
    $quote.setString("coTermDate", $quote.getString("masterContractCoTermDate"));
end',
    '["DEAL_TYPE", "TERM"]',
    '["PRORATE", "COTERM"]',
    'AMENDMENT_PRORATION',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule H: Renewal Uplift and Retention Offer
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-008', id, 'Renewal Uplift and Retention Offer',
    'Applies standard 8% renewal uplift, reduced to 3% with price protection for high-churn-risk customers (score > 70).',
    'RENEWAL', 530, 'ACTIVE',
    'rule "Renewal Uplift and Retention Offer"
salience 530
when
    $quote: QuoteFact(customer != null, $cust: customer)
    eval("RENEWAL".equals($quote.getString("dealType")))
then
    java.math.BigDecimal baseUplift = new java.math.BigDecimal("0.08");
    if ($cust.getChurnScore() != null && $cust.getChurnScore() > 70) {
        baseUplift = new java.math.BigDecimal("0.03");
        $quote.setString("retentionOffer", "ENABLE_12M_PRICE_PROTECTION");
    }
    $quote.setDecimal("renewalUpliftPct", baseUplift);
    $quote.setString("explanationCode", "RENEWAL_UPLIFT_POLICY");
end',
    '["DEAL_TYPE", "CHURN_SCORE"]',
    '["SET_UPLIFT", "RETENTION_OFFER"]',
    'RENEWAL_UPLIFT_POLICY',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';
