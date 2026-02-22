SET search_path TO pricing_ai_rules;

INSERT INTO rule_set (
    id,
    name,
    description,
    version,
    status,
    effective_date,
    expiration_date,
    created_at,
    updated_at,
    created_by,
    updated_by
)
VALUES (
    1,
    'StandardPricing',
    'Seeded rule set with additive legacy pricing AI rules (ARR/TCV/ACV + TCV tiers + ACV minimum commitment).',
    'latest',
    'ACTIVE',
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '20 years',
    NOW(),
    NOW(),
    'system',
    'system'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO rule (
    id,
    rule_set_id,
    name,
    description,
    rule_type,
    content,
    priority,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by
)
VALUES
(
    101,
    1,
    'Calculate ARR',
    'Additive legacy metric derivation: annual recurring revenue from monthly price.',
    'PRICING',
    $drl$
package com.opencrm.pricingairules.rules;
import com.opencrm.rules.model.QuoteFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
rule "Calculate ARR"
salience 420
when
    $quote: QuoteFact(subscriptionType == "ANNUAL")
    eval($quote.getDecimal("monthlyPrice") != null)
then
    BigDecimal monthly = $quote.getDecimal("monthlyPrice");
    BigDecimal arr = monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
    $quote.setDecimal("arr", arr);
end
$drl$,
    420,
    'ACTIVE',
    NOW(),
    NOW(),
    'system',
    'system'
),
(
    102,
    1,
    'Calculate TCV',
    'Additive legacy metric derivation: total contract value from monthly price and duration.',
    'PRICING',
    $drl$
package com.opencrm.pricingairules.rules;
import com.opencrm.rules.model.QuoteFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
rule "Calculate TCV"
salience 419
when
    $quote: QuoteFact(durationInMonths > 0)
    eval($quote.getDecimal("monthlyPrice") != null)
then
    BigDecimal monthly = $quote.getDecimal("monthlyPrice");
    BigDecimal tcv = monthly.multiply(BigDecimal.valueOf($quote.getDurationInMonths()))
        .setScale(2, RoundingMode.HALF_UP);
    $quote.setDecimal("tcv", tcv);
    if ($quote.getBasePrice() == null) {
        $quote.setBasePrice(tcv);
    }
    if ($quote.getFinalPrice() == null) {
        $quote.setFinalPrice($quote.getBasePrice());
    }
end
$drl$,
    419,
    'ACTIVE',
    NOW(),
    NOW(),
    'system',
    'system'
),
(
    103,
    1,
    'Calculate ACV',
    'Additive legacy metric derivation: annual contract value from monthly price and duration.',
    'PRICING',
    $drl$
package com.opencrm.pricingairules.rules;
import com.opencrm.rules.model.QuoteFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
rule "Calculate ACV"
salience 418
when
    $quote: QuoteFact(durationInMonths > 0)
    eval($quote.getDecimal("monthlyPrice") != null)
then
    BigDecimal monthly = $quote.getDecimal("monthlyPrice");
    BigDecimal acv = monthly.multiply(BigDecimal.valueOf($quote.getDurationInMonths()))
        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    $quote.setDecimal("acv", acv);
end
$drl$,
    418,
    'ACTIVE',
    NOW(),
    NOW(),
    'system',
    'system'
),
(
    104,
    1,
    'TCV-based Pricing Tiers',
    'Additive legacy quote-level pricing tiers based on TCV.',
    'PRICING',
    $drl$
package com.opencrm.pricingairules.rules;
import com.opencrm.rules.model.QuoteFact;
import java.math.BigDecimal;
rule "TCV-based Pricing Tiers"
salience 410
when
    $quote: QuoteFact(basePrice != null, finalPrice != null)
    eval($quote.getDecimal("tcv") != null)
then
    BigDecimal tcv = $quote.getDecimal("tcv");
    BigDecimal multiplier = BigDecimal.ONE;
    if (tcv.compareTo(new BigDecimal("5000000")) >= 0) multiplier = new BigDecimal("1.20");
    else if (tcv.compareTo(new BigDecimal("2000000")) >= 0) multiplier = new BigDecimal("1.15");
    else if (tcv.compareTo(new BigDecimal("500000")) >= 0) multiplier = new BigDecimal("1.10");
    BigDecimal finalPrice = $quote.getBasePrice().multiply(multiplier);
    $quote.setFinalPrice(finalPrice);
    $quote.setDecimal("priceMultiplier", multiplier);
    $quote.setString("explanationCode", "TCV_TIER_MULTIPLIER");
end
$drl$,
    410,
    'ACTIVE',
    NOW(),
    NOW(),
    'system',
    'system'
),
(
    105,
    1,
    'ACV-based Minimum Commitment',
    'Additive legacy minimum commitment floor based on ACV.',
    'PRICING',
    $drl$
package com.opencrm.pricingairules.rules;
import com.opencrm.rules.model.QuoteFact;
import java.math.BigDecimal;
rule "ACV-based Minimum Commitment"
salience 409
when
    $quote: QuoteFact(finalPrice != null)
    eval($quote.getDecimal("acv") != null)
then
    BigDecimal acv = $quote.getDecimal("acv");
    BigDecimal finalPrice = $quote.getFinalPrice();
    BigDecimal minCommitment = BigDecimal.ZERO;
    if (acv.compareTo(new BigDecimal("50000")) < 0) minCommitment = new BigDecimal("50000");
    else if (acv.compareTo(new BigDecimal("100000")) < 0) minCommitment = new BigDecimal("100000");
    if (finalPrice.compareTo(minCommitment) < 0) {
        $quote.setFinalPrice(minCommitment);
        $quote.setDecimal("minimumCommitment", minCommitment);
        $quote.setString("explanationCode", "ACV_MIN_COMMITMENT");
    }
end
$drl$,
    409,
    'ACTIVE',
    NOW(),
    NOW(),
    'system',
    'system'
)
ON CONFLICT (id) DO NOTHING;

SELECT setval('pricing_ai_rules.hibernate_sequence', 1000, true);
