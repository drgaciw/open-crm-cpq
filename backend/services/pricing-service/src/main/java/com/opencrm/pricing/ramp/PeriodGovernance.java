package com.opencrm.pricing.ramp;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.engine.*;
import com.opencrm.rules.model.QuoteFact;

import java.math.BigDecimal;
import java.util.*;

/** Interim D-06 policy. Every period is evaluated through the rules port; no placeholder margin fact is inserted. */
public final class PeriodGovernance {
    private static final RuleDefinition POLICY = RuleDefinition.builder()
            .id("ramp-governance-v1").name("Ramp governance").content("""
            package com.opencrm.pricing.rules;
            import com.opencrm.rules.model.QuoteFact;
            import java.math.BigDecimal;
            rule "Ramp discount threshold"
            salience 100
            when
                $quote : QuoteFact()
                eval($quote.getDecimal("discountFraction").compareTo(new BigDecimal("0.25")) > 0)
            then
                $quote.setString("approvalTier", "SALES_VP");
                $quote.setString("explanationCode", "RAMP_DISCOUNT_THRESHOLD");
            end
            rule "Ramp step down"
            salience 90
            when
                $quote : QuoteFact()
                eval(Boolean.TRUE.equals($quote.getBoolean("stepDown")))
                eval(!Boolean.TRUE.equals($quote.getBoolean("stepDownAllowed")))
            then
                if ($quote.getString("approvalTier") == null) $quote.setString("approvalTier", "DEAL_DESK");
                $quote.setString("stepDownReason", "RAMP_STEP_DOWN");
            end
            """).build();

    public void evaluate(PricingContext context, PricingResult result, RulesEngine engine,
                         Map<String, com.opencrm.pricing.product.PricingProduct> products,
                         List<RuleDefinition> configuredRules) {
        // D-06 excludes placeholder margin policy. All other configured policies see the same aggregate metrics.
        List<RuleDefinition> policies = new ArrayList<>(configuredRules.stream()
                .filter(rule -> rule.getContent() == null || !rule.getContent().contains("estimatedMarginPct"))
                .toList());
        policies.add(POLICY);
        boolean tcvTier = policies.stream().anyMatch(rule -> rule.getContent() != null
                && rule.getContent().contains("getDecimal(\"tcv\")"));
        for (int lineIndex = 0; lineIndex < result.getLineResults().size(); lineIndex++) {
            PricingLineResult line = result.getLineResults().get(lineIndex);
            List<PricedPeriod> periods = line.getPricedPeriods();
            if (periods == null || periods.isEmpty()) continue;
            Map<String, Object> safeMetadata = line.getMetadata() == null ? new HashMap<>() : new HashMap<>(line.getMetadata());
            safeMetadata.remove("rampGovernance");
            safeMetadata.put("rampGovernanceEvaluated", true);
            line.setMetadata(safeMetadata);
            PricingLineRequest request = context.getLineItems().get(lineIndex);
            boolean allowed = false;
            com.opencrm.pricing.product.PricingProduct product = products.get(request.getProductExternalId());
            if (product != null && product.getMetadata() != null) {
                try {
                    allowed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(product.getMetadata())
                            .path("rampStepDownAllowed").asBoolean(false);
                } catch (Exception ex) {
                    throw new BusinessException("Invalid product ramp policy", "RAMP_PRODUCT_POLICY_INVALID");
                }
            }
            try {
                String policyContext = policies.stream().sorted(Comparator.comparing(rule -> String.valueOf(rule.getId())))
                        .map(rule -> rule.getId() + "=" + String.valueOf(rule.getContent()).replace("\r\n", "\n")
                                + ":" + String.valueOf(rule.getConditionGroup()) + ":" + String.valueOf(rule.getActionSet()))
                        .reduce("", (left, right) -> left + "\n" + right);
                policyContext += "\nstepDownAllowed=" + allowed + "\nlistPrice=" + line.getListPrice().stripTrailingZeros().toPlainString();
                safeMetadata.put("rampPolicyContext", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(policyContext.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
            PricedPeriod previous = null;
            for (PricedPeriod period : periods) {
                boolean stepDown = previous != null
                        && (period.getQuantity() < previous.getQuantity()
                        || period.getNetUnitPrice().compareTo(previous.getNetUnitPrice()) < 0);
                QuoteFact fact = QuoteFact.builder().externalId(context.getQuoteExternalId())
                        .durationInMonths(context.getDurationInMonths()).currency(context.getCurrency())
                        .basePrice(result.getFinancialMetrics().getTcv())
                        .finalPrice(tcvTier ? null : result.getFinancialMetrics().getTcv()).build();
                fact.setDecimal("discountFraction", period.getDiscountRate());
                fact.setDecimal("tcv", result.getFinancialMetrics().getTcv());
                fact.setDecimal("acv", result.getFinancialMetrics().getAcv());
                fact.setBoolean("stepDown", stepDown);
                fact.setBoolean("stepDownAllowed", allowed);
                RuleExecutionContext ruleContext = RuleExecutionContext.builder().engineType(RulesEngineType.DROOLS).build();
                ruleContext.getFacts().add(fact);
                RuleExecutionResult decision = engine.execute(ruleContext, policies);
                if (decision == null || !decision.isSuccess()) {
                    throw new BusinessException("Ramp governance evaluation failed", "RULE_EXECUTION_ERROR");
                }
                String tier = canonicalTier(fact.getString("approvalTier"));
                stepDown = fact.getString("stepDownReason") != null;
                // Owner-delegated R2 choice: explicit negotiated periods stay fixed. A configured price policy
                // requiring a different commitment is a Deal Desk exception, never a silent period reallocation.
                boolean commitmentMismatch = fact.getFinalPrice() != null
                        && fact.getFinalPrice().compareTo(result.getFinancialMetrics().getTcv()) != 0;
                if (commitmentMismatch && tier == null) tier = "DEAL_DESK";
                if (tier != null) {
                    if (rank(tier) > rank(result.getApprovalTier())) result.setApprovalTier(tier);
                    result.setApprovalState("PENDING");
                    List<String> reasons = new ArrayList<>();
                    if (fact.getString("explanationCode") != null) reasons.add(fact.getString("explanationCode"));
                    if (fact.getString("stepDownReason") != null) reasons.add(fact.getString("stepDownReason"));
                    if (commitmentMismatch) reasons.add("RAMP_POLICY_COMMITMENT: required "
                            + fact.getFinalPrice().stripTrailingZeros().toPlainString() + " " + context.getCurrency()
                            + ", scheduled " + result.getFinancialMetrics().getTcv().stripTrailingZeros().toPlainString());
                    Map<String, Object> governance = new LinkedHashMap<>();
                    governance.put("governingPeriod", period.getSequence());
                    governance.put("discountPercent", period.getDiscountRate().multiply(BigDecimal.valueOf(100)));
                    governance.put("stepDown", stepDown);
                    governance.put("reasons", reasons);
                    governance.put("approvalTier", tier);
                    Map<String, Object> metadata = line.getMetadata() == null
                            ? new HashMap<>() : new HashMap<>(line.getMetadata());
                    Map<?, ?> prior = metadata.get("rampGovernance") instanceof Map<?, ?> map ? map : null;
                    boolean priorStepDown = prior != null && Boolean.TRUE.equals(prior.get("stepDown"));
                    if (priorStepDown || stepDown) {
                        governance.put("stepDown", true);
                        if (!reasons.contains("RAMP_STEP_DOWN")) reasons.add("RAMP_STEP_DOWN");
                    }
                    if (prior == null || rank(tier) > rank((String) prior.get("approvalTier"))
                            || (tier.equals(prior.get("approvalTier")) && period.getDiscountRate().multiply(BigDecimal.valueOf(100))
                            .compareTo(new BigDecimal(prior.get("discountPercent").toString())) > 0)) {
                        metadata.put("rampGovernance", governance);
                    } else if (stepDown && prior instanceof Map<?, ?>) {
                        Map<String, Object> combined = new LinkedHashMap<>();
                        prior.forEach((key, value) -> combined.put(key.toString(), value));
                        combined.put("stepDown", true);
                        List<String> combinedReasons = new ArrayList<>((List<String>) prior.get("reasons"));
                        if (!combinedReasons.contains("RAMP_STEP_DOWN")) combinedReasons.add("RAMP_STEP_DOWN");
                        combined.put("reasons", combinedReasons);
                        metadata.put("rampGovernance", combined);
                    }
                    line.setMetadata(metadata);
                    result.getExplanationCodes().addAll(reasons);
                }
                previous = period;
            }
        }
    }

    private String canonicalTier(String tier) {
        if (tier == null) return null;
        return switch (tier) {
            case "MANAGER", "DEAL_DESK" -> "DEAL_DESK";
            case "DIRECTOR", "VP_SALES", "SALES_VP" -> "SALES_VP";
            case "CFO" -> "CFO";
            default -> throw new BusinessException("Unknown ramp policy approval tier", "RAMP_POLICY_TIER_INVALID");
        };
    }
    private int rank(String tier) {
        return tier == null ? 0 : switch (canonicalTier(tier)) { case "CFO" -> 3; case "SALES_VP" -> 2; default -> 1; };
    }
}
