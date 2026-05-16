package com.opencrm.rules.openl;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngine;
import com.opencrm.rules.engine.RulesEngineType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenL Tablets scaffolding: executes simple {@code openl-table:} descriptors until full OpenL runtime is wired.
 */
@Slf4j
@Service
public class OpenLRulesEngine implements RulesEngine {

    private static final Pattern DISCOUNT_RATE = Pattern.compile("discountRate=([0-9.]+)");

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context) {
        throw new UnsupportedOperationException("Use execute(context, rules)");
    }

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context, List<RuleDefinition> rules) {
        long start = System.currentTimeMillis();
        Map<String, Object> outputs = new HashMap<>();
        List<String> fired = new ArrayList<>();
        int firedCount = 0;

        for (RuleDefinition rule : rules) {
            if (rule.getContent() == null || !rule.getContent().trim().startsWith("openl-table:")) {
                log.debug("Skipping non-table OpenL rule {}", rule.getId());
                continue;
            }
            BigDecimal discountRate = parseDiscountRate(rule.getContent());
            applyDiscountRate(context.getFacts(), discountRate);
            outputs.put("openl.discountRate", discountRate);
            outputs.put("openl.rule." + rule.getId(), discountRate);
            fired.add(rule.getName());
            firedCount++;
        }

        return RuleExecutionResult.builder()
                .success(true)
                .rulesFired(firedCount)
                .firedRuleNames(fired)
                .outputs(outputs)
                .executionTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private BigDecimal parseDiscountRate(String content) {
        Matcher matcher = DISCOUNT_RATE.matcher(content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("OpenL table content must declare discountRate=");
        }
        return new BigDecimal(matcher.group(1));
    }

    private void applyDiscountRate(List<Object> facts, BigDecimal discountRate) {
        for (Object fact : facts) {
            applyViaReflection(fact, discountRate);
        }
    }

    private void applyViaReflection(Object fact, BigDecimal discountRate) {
        try {
            Method listPriceGetter = fact.getClass().getMethod("getListPrice");
            Object listPriceValue = listPriceGetter.invoke(fact);
            if (listPriceValue instanceof BigDecimal listPrice) {
                BigDecimal discountAmount = listPrice.multiply(discountRate);
                Method discountRateSetter = fact.getClass().getMethod("setDiscountRate", BigDecimal.class);
                discountRateSetter.invoke(fact, discountRate);
                try {
                    Method discountAmountSetter = fact.getClass().getMethod("setDiscountAmount", BigDecimal.class);
                    discountAmountSetter.invoke(fact, discountAmount);
                } catch (ReflectiveOperationException ignored) {
                    // optional field
                }
                try {
                    BigDecimal unitPrice = listPrice.subtract(discountAmount);
                    Method unitPriceSetter = fact.getClass().getMethod("setUnitPrice", BigDecimal.class);
                    unitPriceSetter.invoke(fact, unitPrice);
                } catch (ReflectiveOperationException ignored) {
                    // optional field
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fact type does not expose pricing fields; skip.
        }
    }

    @Override
    public void invalidateCache(String ruleSetId) {
        log.debug("OpenL cache invalidation not required for scaffolding rule set {}", ruleSetId);
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public RulesEngineType getEngineType() {
        return RulesEngineType.OPENL_TABLETS;
    }
}
