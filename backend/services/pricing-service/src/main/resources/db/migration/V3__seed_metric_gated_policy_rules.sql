SET search_path TO pricing;

-- T1-3 additive metric-gated policy rules (hybrid §5.1a).
-- Skipped: Volume Discount Based on ARR (Phase 0 overlap with Named Seat Volume Discount).
-- Skipped: sports / vertical packs.

-- Rule I: TCV-based Pricing Tiers
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-009', id, 'TCV-based Pricing Tiers',
    'Applies quote-level price multipliers from precomputed TCV tiers when finalPrice is unset.',
    'PRICING', 550, 'ACTIVE',
    'rule "TCV-based Pricing Tiers"
salience 550
when
    $quote: QuoteFact(basePrice != null, finalPrice == null, $basePrice: basePrice)
    eval($quote.getDecimal("tcv") != null)
then
    java.math.BigDecimal tcv = $quote.getDecimal("tcv");
    java.math.BigDecimal priceMultiplier = java.math.BigDecimal.ONE;
    if (tcv.compareTo(new java.math.BigDecimal("5000000")) >= 0)
        priceMultiplier = new java.math.BigDecimal("1.20");
    else if (tcv.compareTo(new java.math.BigDecimal("2000000")) >= 0)
        priceMultiplier = new java.math.BigDecimal("1.15");
    else if (tcv.compareTo(new java.math.BigDecimal("500000")) >= 0)
        priceMultiplier = new java.math.BigDecimal("1.10");
    $quote.setFinalPrice($basePrice.multiply(priceMultiplier));
    $quote.setDecimal("priceMultiplier", priceMultiplier);
    $quote.setString("explanationCode", "TCV_MULTIPLIER");
end',
    '["TCV"]',
    '["SET_PRICE_MULTIPLIER"]',
    'TCV_MULTIPLIER',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule J: ACV-based Minimum Commitment
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-010', id, 'ACV-based Minimum Commitment',
    'Raises quote finalPrice to ACV-band minimum commitment floors when below the floor.',
    'PRICING', 545, 'ACTIVE',
    'rule "ACV-based Minimum Commitment"
salience 545
when
    $quote: QuoteFact(finalPrice != null, $finalPrice: finalPrice)
    eval($quote.getDecimal("acv") != null)
then
    java.math.BigDecimal acv = $quote.getDecimal("acv");
    java.math.BigDecimal minCommitment = java.math.BigDecimal.ZERO;
    if (acv.compareTo(new java.math.BigDecimal("50000")) < 0)
        minCommitment = new java.math.BigDecimal("50000");
    else if (acv.compareTo(new java.math.BigDecimal("100000")) < 0)
        minCommitment = new java.math.BigDecimal("100000");
    if (minCommitment.compareTo(java.math.BigDecimal.ZERO) > 0
            && $finalPrice.compareTo(minCommitment) < 0) {
        $quote.setFinalPrice(minCommitment);
        $quote.setDecimal("minCommitment", minCommitment);
        $quote.setString("explanationCode", "ACV_MIN_COMMITMENT");
    }
end',
    '["ACV"]',
    '["SET_MIN_COMMITMENT"]',
    'ACV_MIN_COMMITMENT',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';

-- Rule K: Churn Risk Adjustment (non-renewal only; renewals use rule-008)
INSERT INTO rules (uuid, rule_set_id, name, description, rule_type, priority, status, content, condition_group, action_set, explanation_code, created_at, created_by)
SELECT 'rule-011', id, 'Churn Risk Adjustment',
    'Applies quote-level churn discount for non-renewal deals; renewals stay on Renewal Uplift and Retention Offer.',
    'PRICING', 535, 'ACTIVE',
    'rule "Churn Risk Adjustment"
salience 535
when
    $quote: QuoteFact(finalPrice != null, $finalPrice: finalPrice, customer != null, $cust: customer)
    eval($cust.getChurnScore() != null
         && !"RENEWAL".equals($quote.getString("dealType")))
then
    Integer churnScore = $cust.getChurnScore();
    java.math.BigDecimal churnAdjustment = java.math.BigDecimal.ZERO;
    if (churnScore > 70)
        churnAdjustment = new java.math.BigDecimal("-0.05");
    else if (churnScore > 50)
        churnAdjustment = new java.math.BigDecimal("-0.03");
    if (churnAdjustment.compareTo(java.math.BigDecimal.ZERO) != 0) {
        $quote.setFinalPrice($finalPrice.multiply(java.math.BigDecimal.ONE.add(churnAdjustment)));
        $quote.setDecimal("churnAdjustment", churnAdjustment);
        $quote.setString("explanationCode", "CHURN_ADJUSTMENT");
    }
end',
    '["CHURN_SCORE", "DEAL_TYPE"]',
    '["APPLY_CHURN_ADJUSTMENT"]',
    'CHURN_ADJUSTMENT',
    NOW(), 'system'
FROM rule_sets WHERE uuid = 'rs-default-001';
