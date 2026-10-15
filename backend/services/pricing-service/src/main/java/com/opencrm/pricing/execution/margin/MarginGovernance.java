package com.opencrm.pricing.execution.margin;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngine;
import com.opencrm.rules.engine.RulesEngineType;
import com.opencrm.rules.model.QuoteFact;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cost-basis margin governance (AC-16 and the fail-closed rule). Runs only when
 * {@code opencrm.pricing.margin.source=cost-basis}.
 *
 * <p>The 35% floor is policy, so it is evaluated through the rules port with {@code approval-margin-exception.drl},
 * as {@code PeriodGovernance} evaluates its built-in ramp policy. Each line, or each ramp period, is one
 * {@link QuoteFact} whose {@code estimatedMarginPct} attribute carries the margin <em>fraction</em>: that is the
 * attribute's meaning on the rules side (the DRL and the seeded rule compare it with 0.35). The response field of
 * the same name stays a percent and is never copied onto a fact.
 *
 * <p>A missing cost is not policy but a precondition, so it routes to {@code DEAL_DESK} in Java, like
 * {@code PeriodGovernance}'s commitment mismatch. Within a line, a unit with no margin governs ahead of any computed
 * margin, and otherwise the lowest margin under the floor governs. The governing period is named in the line's
 * {@code marginGovernance} metadata, as {@code rampGovernance} names it for discounts.
 */
public final class MarginGovernance {

    public static final String BELOW_FLOOR = "MARGIN_BELOW_FLOOR";
    static final String MARGIN_KEY = "margin";
    static final String GOVERNANCE_KEY = "marginGovernance";
    private static final String DEAL_DESK = "DEAL_DESK";
    private static final String FACT_MARGIN = "estimatedMarginPct";
    private static final RuleDefinition POLICY = RuleDefinition.builder()
            .id("approval-margin-exception").name("Route Approval for Margin Exception")
            .content(classpathRule("/rules/approval-margin-exception.drl")).build();

    public void evaluate(PricingResult result, MarginAssessment assessment, RulesEngine engine, String priceCurrency) {
        List<PricingLineResult> lines = result.getLineResults();
        Map<String, QuoteFact> facts = new LinkedHashMap<>();
        for (LineMargin line : assessment.getLines()) {
            if (line.getPeriods() != null) {
                for (PeriodMargin period : line.getPeriods()) {
                    if (period.getMarginFraction() != null) {
                        facts.put(key(line, period.getSequence()), fact(key(line, period.getSequence()),
                                period.getNet(), period.getMarginFraction(), priceCurrency));
                    }
                }
            } else if (line.getMarginFraction() != null) {
                facts.put(key(line, null), fact(key(line, null), line.getNet(), line.getMarginFraction(), priceCurrency));
            }
        }
        if (!facts.isEmpty()) {
            RuleExecutionContext context = RuleExecutionContext.builder().engineType(RulesEngineType.DROOLS).build();
            context.getFacts().addAll(facts.values());
            RuleExecutionResult decision = engine.execute(context, List.of(POLICY));
            if (decision == null || !decision.isSuccess()) {
                throw new BusinessException("Margin governance evaluation failed", "RULE_EXECUTION_ERROR");
            }
        }

        for (LineMargin margin : assessment.getLines()) {
            PricingLineResult line = lines.get(margin.getLineIndex());
            Map<String, Object> metadata = line.getMetadata() == null ? new HashMap<>() : new HashMap<>(line.getMetadata());
            // Server evidence only: a caller or a configured rule cannot supply either key.
            metadata.remove(GOVERNANCE_KEY);
            metadata.put(MARGIN_KEY, margin);
            Map<String, Object> governance = governing(margin, facts, priceCurrency);
            if (governance != null) {
                metadata.put(GOVERNANCE_KEY, governance);
                route(result, (List<?>) governance.get("reasons"));
            }
            line.setMetadata(metadata);
        }
    }

    private Map<String, Object> governing(LineMargin line, Map<String, QuoteFact> facts, String priceCurrency) {
        Integer period = null;
        BigDecimal fraction = null;
        String reason = null;
        if (line.getPeriods() != null) {
            for (PeriodMargin candidate : line.getPeriods()) {
                if (candidate.getMissingCostReason() != null) {
                    period = candidate.getSequence();
                    fraction = null;
                    reason = missingReason(candidate.getMissingCostReason(), line.getProductExternalId(),
                            candidate.getUnitCost(), priceCurrency);
                    break;
                }
                if (belowFloor(facts.get(key(line, candidate.getSequence())))
                        && (fraction == null || candidate.getMarginFraction().compareTo(fraction) < 0)) {
                    period = candidate.getSequence();
                    fraction = candidate.getMarginFraction();
                    reason = BELOW_FLOOR;
                }
            }
        } else if (line.getMissingCostReason() != null) {
            reason = missingReason(line.getMissingCostReason(), line.getProductExternalId(), line.getUnitCost(),
                    priceCurrency);
        } else if (belowFloor(facts.get(key(line, null)))) {
            fraction = line.getMarginFraction();
            reason = BELOW_FLOOR;
        }
        if (reason == null) {
            return null;
        }
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("governingPeriod", period);
        governance.put("productExternalId", line.getProductExternalId());
        governance.put("marginFraction", fraction);
        governance.put("marginPercent", fraction == null ? null
                : fraction.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.FLOOR));
        governance.put("reasons", new ArrayList<>(List.of(reason)));
        governance.put("approvalTier", DEAL_DESK);
        return governance;
    }

    private static void route(PricingResult result, List<?> reasons) {
        if (rank(result.getApprovalTier()) < rank(DEAL_DESK)) {
            result.setApprovalTier(DEAL_DESK);
        }
        result.setApprovalState("PENDING");
        if (result.getExplanationCodes() == null) {
            result.setExplanationCodes(new ArrayList<>());
        }
        for (Object reason : reasons) {
            if (!result.getExplanationCodes().contains(reason.toString())) {
                result.getExplanationCodes().add(reason.toString());
            }
        }
    }

    private static String missingReason(String code, String product, CostEvidence evidence, String priceCurrency) {
        if (CostBasisMarginEvaluator.COST_CURRENCY_MISMATCH.equals(code) && evidence != null) {
            return code + ": " + product + " (cost " + evidence.getCurrency() + ", price " + priceCurrency + ")";
        }
        return code + ": " + product;
    }

    private static boolean belowFloor(QuoteFact fact) {
        return fact != null && BELOW_FLOOR.equals(fact.getString("explanationCode"));
    }

    private static QuoteFact fact(String id, BigDecimal net, BigDecimal fraction, String currency) {
        QuoteFact fact = QuoteFact.builder().externalId(id).currency(currency).basePrice(net).finalPrice(net).build();
        fact.setDecimal(FACT_MARGIN, fraction);
        return fact;
    }

    private static String key(LineMargin line, Integer period) {
        return "line-" + line.getLineIndex() + (period == null ? "" : "-period-" + period);
    }

    private static int rank(String tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case "CFO" -> 3;
            case "DIRECTOR", "VP_SALES", "SALES_VP" -> 2;
            case "MANAGER", "DEAL_DESK" -> 1;
            default -> 0;
        };
    }

    private static String classpathRule(String path) {
        try (InputStream in = MarginGovernance.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing margin policy " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unreadable margin policy " + path, ex);
        }
    }
}
