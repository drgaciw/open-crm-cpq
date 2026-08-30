package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared product-rule evaluation core for Wave G / S2B-8.
 * Used by {@link StatelessConfigurationEvaluator} (agent path) and
 * {@link com.opencrm.configuration.constraint.ConstraintSolver} (session path).
 *
 * <p>Hard failures ({@code valid=false}): REQUIRES, EXCLUDES, VALIDATES.
 * Soft warnings (recorded but do not alone fail): RECOMMENDS.
 * DEFAULT: adds missing {@code targetProductId} to the working selection, then re-evaluates.
 */
public final class ProductRuleEvaluationCore {

    public static final String REQUIRES = "REQUIRES";
    public static final String EXCLUDES = "EXCLUDES";
    public static final String RECOMMENDS = "RECOMMENDS";
    public static final String DEFAULT = "DEFAULT";
    public static final String VALIDATES = "VALIDATES";

    private ProductRuleEvaluationCore() {
    }

    public static boolean isHardFailure(String ruleType) {
        if (ruleType == null) {
            return false;
        }
        String t = ruleType.trim().toUpperCase(Locale.ROOT);
        return REQUIRES.equals(t) || EXCLUDES.equals(t) || VALIDATES.equals(t);
    }

    /**
     * Evaluate rules for the given selected product IDs.
     *
     * @param selectedProductIds initial selection (may be null/empty)
     * @param rulesForProduct    loads rules for a source product id
     */
    public static ConfigurationValidationResult evaluate(
            List<Long> selectedProductIds,
            Function<Long, List<ProductConfigurationRuleDto>> rulesForProduct) {
        if (selectedProductIds == null || selectedProductIds.isEmpty()) {
            return ConfigurationValidationResult.ok();
        }

        Set<Long> working = new HashSet<>(selectedProductIds);
        List<ProductConfigurationRuleDto> allRules = new ArrayList<>();

        for (Long productId : List.copyOf(working)) {
            List<ProductConfigurationRuleDto> rules = rulesForProduct.apply(productId);
            if (rules == null) {
                continue;
            }
            allRules.addAll(rules);
        }

        // DEFAULT: expand working set with missing targets when source is selected
        for (ProductConfigurationRuleDto rule : allRules) {
            if (rule == null || !DEFAULT.equals(normalize(rule.getRuleType()))) {
                continue;
            }
            Long source = rule.getSourceProductId();
            Long target = rule.getTargetProductId();
            if (source != null && working.contains(source) && target != null) {
                working.add(target);
            }
        }

        // Fetch rules for any newly added DEFAULT targets
        Set<Long> fetched = new HashSet<>(selectedProductIds);
        for (Long productId : List.copyOf(working)) {
            if (fetched.contains(productId)) {
                continue;
            }
            List<ProductConfigurationRuleDto> rules = rulesForProduct.apply(productId);
            if (rules != null) {
                allRules.addAll(rules);
            }
            fetched.add(productId);
        }

        List<ConfigurationViolation> violations = new ArrayList<>();
        for (ProductConfigurationRuleDto rule : allRules) {
            if (rule == null || rule.getRuleType() == null) {
                continue;
            }
            String type = normalize(rule.getRuleType());
            Long source = rule.getSourceProductId();
            if (source == null || !working.contains(source)) {
                // Only enforce rules whose source is in the (expanded) selection
                continue;
            }
            switch (type) {
                case REQUIRES -> evaluateRequires(source, rule, working, violations);
                case EXCLUDES -> evaluateExcludes(source, rule, working, violations);
                case VALIDATES -> evaluateValidates(source, rule, working, violations);
                case RECOMMENDS -> evaluateRecommends(source, rule, working, violations);
                case DEFAULT -> {
                    // already applied
                }
                default -> {
                    // ignore unknown types
                }
            }
        }

        return ConfigurationValidationResult.of(violations);
    }

    private static String normalize(String ruleType) {
        return ruleType == null ? "" : ruleType.trim().toUpperCase(Locale.ROOT);
    }

    private static void evaluateRequires(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && !productSet.contains(targetId)) {
            violations.add(violation(rule, REQUIRES,
                    defaultMessage(rule, "Product " + productId + " requires product " + targetId),
                    "Add required product " + targetId + " to the selection"));
        }
    }

    private static void evaluateExcludes(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && productSet.contains(targetId)) {
            violations.add(violation(rule, EXCLUDES,
                    defaultMessage(rule, "Product " + productId + " is incompatible with product " + targetId),
                    "Remove product " + targetId + " or product " + productId + " from the selection"));
        }
    }

    private static void evaluateValidates(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && !productSet.contains(targetId)) {
            violations.add(violation(rule, VALIDATES,
                    defaultMessage(rule, "Product " + productId + " validation requires product " + targetId),
                    "Add product " + targetId + " required by VALIDATES rule"));
        }
        // Attribute/expression-only VALIDATES: deferred (no expression engine in G1)
    }

    private static void evaluateRecommends(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && !productSet.contains(targetId)) {
            violations.add(violation(rule, RECOMMENDS,
                    defaultMessage(rule, "Product " + productId + " recommends product " + targetId),
                    "Consider adding recommended product " + targetId));
        }
    }

    private static String defaultMessage(ProductConfigurationRuleDto rule, String fallback) {
        return rule.getErrorMessage() != null ? rule.getErrorMessage() : fallback;
    }

    private static ConfigurationViolation violation(
            ProductConfigurationRuleDto rule,
            String ruleType,
            String description,
            String remediation) {
        return ConfigurationViolation.builder()
                .ruleId(rule.getId())
                .ruleType(ruleType)
                .description(description)
                .remediation(remediation)
                .build();
    }
}
