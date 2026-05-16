package com.opencrm.pricing.rules;

import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RuleExecutionTraceEntry;
import com.opencrm.rules.engine.RulesEngineType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconciles OpenL table discounts with Drools programmatic discounts using sequential compounding
 * so the same list price is never discounted twice additively.
 */
@Component
public class HybridDiscountPrecedencePolicy {

    private static final Pattern DISCOUNT_RATE = Pattern.compile("discountRate=([0-9.]+)");

    public void enforce(List<PricingLineResult> lineResults, RuleExecutionResult ruleResult) {
        if (ruleResult == null || ruleResult.getTrace() == null || ruleResult.getTrace().isEmpty()) {
            return;
        }

        List<RuleExecutionTraceEntry> trace = ruleResult.getTrace();
        boolean openLApplied = trace.stream().anyMatch(e -> e.getEngineType() == RulesEngineType.OPENL_TABLETS);
        boolean droolsApplied = trace.stream().anyMatch(e -> e.getEngineType() == RulesEngineType.DROOLS);
        if (!openLApplied || !droolsApplied) {
            return;
        }

        BigDecimal openLRate = extractOpenLRate(ruleResult);
        BigDecimal droolsRate = extractDroolsRate(ruleResult);
        if (openLRate == null || droolsRate == null) {
            return;
        }

        BigDecimal compoundedRate = compoundSequential(openLRate, droolsRate);

        for (PricingLineResult line : lineResults) {
            if (line.getListPrice() == null || line.getDiscountRate() == null) {
                continue;
            }
            if (line.getDiscountRate().compareTo(compoundedRate) <= 0) {
                continue;
            }
            applyDiscount(line, compoundedRate);
        }
    }

    private BigDecimal extractOpenLRate(RuleExecutionResult ruleResult) {
        return ruleResult.getTrace().stream()
                .filter(entry -> entry.getEngineType() == RulesEngineType.OPENL_TABLETS)
                .map(entry -> parseRateFromRuleId(entry.getRuleId()))
                .filter(rate -> rate != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal extractDroolsRate(RuleExecutionResult ruleResult) {
        return ruleResult.getTrace().stream()
                .filter(entry -> entry.getEngineType() == RulesEngineType.DROOLS)
                .map(entry -> parseRateFromRuleId(entry.getRuleId()))
                .filter(rate -> rate != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal parseRateFromRuleId(String ruleId) {
        if (ruleId == null) {
            return null;
        }
        Matcher matcher = DISCOUNT_RATE.matcher(ruleId);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return null;
    }

    BigDecimal compoundSequential(BigDecimal openLRate, BigDecimal droolsRate) {
        BigDecimal openLFactor = BigDecimal.ONE.subtract(openLRate);
        BigDecimal droolsFactor = BigDecimal.ONE.subtract(droolsRate);
        return BigDecimal.ONE.subtract(openLFactor.multiply(droolsFactor));
    }

    private void applyDiscount(PricingLineResult line, BigDecimal discountRate) {
        BigDecimal listPrice = line.getListPrice();
        int quantity = line.getQuantity() != null ? line.getQuantity() : 1;
        BigDecimal discountAmount = listPrice.multiply(discountRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal unitPrice = listPrice.subtract(discountAmount);
        line.setDiscountRate(discountRate);
        line.setDiscountAmount(discountAmount);
        line.setUnitPrice(unitPrice);
        line.setFinalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
}
