package com.opencrm.pricing.ramp;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.ramp.migration.HybridMigrationPolicy;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.engine.*;
import com.opencrm.rules.model.CustomerFact;
import com.opencrm.rules.model.QuoteFact;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
            rule "Hybrid migration window"
            salience 80
            when
                $quote : QuoteFact()
                eval(Boolean.TRUE.equals($quote.getBoolean("hybridWindow")))
            then
                if ($quote.getString("approvalTier") == null) $quote.setString("approvalTier", "DEAL_DESK");
                $quote.setString("hybridWindowReason", "RAMP_HYBRID_WINDOW");
            end
            """).build();

    /** Classpath rule executed on calculate. The database seed is not this session. */
    private static final RuleDefinition RENEWAL_UPLIFT = RuleDefinition.builder()
            .id("renewal-uplift.drl").name("Renewal Uplift and Retention Offer")
            .content(classpathRule("/rules/renewal-uplift.drl")).build();

    public void evaluate(PricingContext context, PricingResult result, RulesEngine engine,
                         Map<String, com.opencrm.pricing.product.PricingProduct> products,
                         List<RuleDefinition> configuredRules) {
        evaluate(context, result, engine, products, configuredRules, HybridMigrationPolicy.Plan.empty());
    }

    public void evaluate(PricingContext context, PricingResult result, RulesEngine engine,
                         Map<String, com.opencrm.pricing.product.PricingProduct> products,
                         List<RuleDefinition> configuredRules, HybridMigrationPolicy.Plan migrationPlan) {
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
            applyRenewalUpliftDefault(context, line, engine);
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
                if (migrationPlan.hasLine(lineIndex)) policyContext += "\n" + migrationPlan.policyContext(lineIndex);
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
                // RENEWAL only. Other deal types stay off this fact so amendment proration cannot NPE here.
                if ("RENEWAL".equals(exactDealType(context))) fact.setString("dealType", "RENEWAL");
                fact.setDecimal("discountFraction", period.getDiscountRate());
                fact.setDecimal("tcv", result.getFinancialMetrics().getTcv());
                fact.setDecimal("acv", result.getFinancialMetrics().getAcv());
                fact.setBoolean("stepDown", stepDown);
                fact.setBoolean("stepDownAllowed", allowed || migrationPlan.allowsStepDown(lineIndex, period));
                fact.setBoolean("hybridWindow", migrationPlan.hasLine(lineIndex));
                RuleExecutionContext ruleContext = RuleExecutionContext.builder().engineType(RulesEngineType.DROOLS).build();
                ruleContext.getFacts().add(fact);
                RuleExecutionResult decision = engine.execute(ruleContext, policies);
                if (decision == null || !decision.isSuccess()) {
                    throw new BusinessException("Ramp governance evaluation failed", "RULE_EXECUTION_ERROR");
                }
                String tier = canonicalTier(fact.getString("approvalTier"));
                // A configured rule may clear facts after the built-in rule. Validated migration
                // authority still requires approval; mutable rule facts cannot waive that minimum.
                if (migrationPlan.hasLine(lineIndex) && rank(tier) < rank("DEAL_DESK")) tier = "DEAL_DESK";
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
                    if (migrationPlan.hasLine(lineIndex)) reasons.add("RAMP_HYBRID_WINDOW");
                    if (commitmentMismatch) reasons.add("RAMP_POLICY_COMMITMENT: required "
                            + fact.getFinalPrice().stripTrailingZeros().toPlainString() + " " + context.getCurrency()
                            + ", scheduled " + result.getFinancialMetrics().getTcv().stripTrailingZeros().toPlainString());
                    Map<String, Object> governance = new LinkedHashMap<>();
                    governance.put("governingPeriod", period.getSequence());
                    governance.put("discountPercent", period.getDiscountRate().multiply(BigDecimal.valueOf(100)));
                    governance.put("stepDown", stepDown);
                    governance.put("reasons", reasons);
                    governance.put("approvalTier", tier);
                    if (migrationPlan.hasLine(lineIndex)) governance.put("hybridMigration", migrationPlan.input(lineIndex));
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

    /**
     * Runs {@code renewal-uplift.drl} once per line. The fraction is recorded and not applied:
     * uplift basis, caps, and policy precedence are DRG-549/567. A caller-supplied fraction or
     * {@code renewalUpliftSupplied} wins, because this rule is only the default.
     */
    public void applyRenewalUpliftDefault(PricingContext context, PricingLineResult line, RulesEngine engine) {
        if (!"RENEWAL".equals(exactDealType(context)) || line == null) return;
        Map<String, Object> requestMetadata = context.getMetadata();
        QuoteFact fact = QuoteFact.builder().externalId(context.getQuoteExternalId())
                .durationInMonths(context.getDurationInMonths() == null ? 0 : context.getDurationInMonths())
                .currency(context.getCurrency())
                .customer(CustomerFact.builder().churnScore(churnScore(requestMetadata)).build()).build();
        fact.setString("dealType", "RENEWAL");
        Object suppliedPct = requestMetadata.get("renewalUpliftPct");
        boolean supplied = suppliedPct != null || Boolean.TRUE.equals(bool(requestMetadata.get("renewalUpliftSupplied")));
        if (suppliedPct != null) fact.setDecimal("renewalUpliftPct", toDecimal(suppliedPct));
        if (supplied) fact.setBoolean("renewalUpliftSupplied", true);
        RuleExecutionContext ruleContext = RuleExecutionContext.builder().engineType(RulesEngineType.DROOLS).build();
        ruleContext.getFacts().add(fact);
        RuleExecutionResult decision;
        try {
            decision = engine.execute(ruleContext, List.of(RENEWAL_UPLIFT));
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException business) throw business;
            throw new BusinessException("Renewal uplift evaluation failed", "RULE_EXECUTION_ERROR");
        }
        if (decision == null || !decision.isSuccess()) {
            throw new BusinessException("Renewal uplift evaluation failed", "RULE_EXECUTION_ERROR");
        }
        Map<String, Object> metadata = line.getMetadata() == null ? new HashMap<>() : line.getMetadata();
        if (supplied) {
            metadata.put("renewalUpliftSource", "CALLER");
            if (fact.getDecimal("renewalUpliftPct") != null) {
                metadata.put("renewalUpliftPct", fact.getDecimal("renewalUpliftPct").toPlainString());
            }
            line.setMetadata(metadata);
            return;
        }
        BigDecimal pct = fact.getDecimal("renewalUpliftPct");
        if (pct == null) return;
        metadata.put("renewalUpliftPct", pct.toPlainString());
        metadata.put("renewalUpliftSource", "RENEWAL_UPLIFT_POLICY");
        if (fact.getString("retentionOffer") != null) metadata.put("retentionOffer", fact.getString("retentionOffer"));
        line.setMetadata(metadata);
    }

    private static String exactDealType(PricingContext context) {
        if (context == null || context.getMetadata() == null) return null;
        Object value = context.getMetadata().get("dealType");
        return value == null ? null : value.toString();
    }

    private static Integer churnScore(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object value = metadata.get("churnScore");
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof String text) return Boolean.valueOf(text);
        return null;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException("Renewal uplift percent is not a decimal", "INVALID_CONTEXT");
            }
        }
        throw new BusinessException("Renewal uplift percent is not a decimal", "INVALID_CONTEXT");
    }

    private static String classpathRule(String path) {
        try (var stream = PeriodGovernance.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
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
