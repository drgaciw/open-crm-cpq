package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared product-rule evaluation core for Wave G / S2B-8 (+ Wave H VALIDATES expressions).
 * Used by {@link StatelessConfigurationEvaluator} (agent path) and
 * {@link com.opencrm.configuration.constraint.ConstraintSolver} (session path).
 *
 * <p>Hard failures ({@code valid=false}): REQUIRES, EXCLUDES, VALIDATES.
 * Soft warnings (recorded but do not alone fail): RECOMMENDS.
 * DEFAULT: adds missing {@code targetProductId} to the working selection, then re-evaluates.
 *
 * <p>VALIDATES {@code conditionExpression} dialect (Wave H1): {@code attr:key=value}
 * and {@code attr:key!=value}. When attributes are null/empty the expression branch
 * is skipped (agent {@code List<Long>} path must not false-fail).
 */
public final class ProductRuleEvaluationCore {

    private static final Logger log = LoggerFactory.getLogger(ProductRuleEvaluationCore.class);

    public static final String REQUIRES = "REQUIRES";
    public static final String EXCLUDES = "EXCLUDES";
    public static final String RECOMMENDS = "RECOMMENDS";
    public static final String DEFAULT = "DEFAULT";
    public static final String VALIDATES = "VALIDATES";

    private static final String ATTR_PREFIX = "attr:";

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
     * Evaluate rules for the given selected product IDs without session attributes
     * (agent / {@code List<Long>} path — expression VALIDATES are skipped).
     */
    public static ConfigurationValidationResult evaluate(
            List<Long> selectedProductIds,
            Function<Long, List<ProductConfigurationRuleDto>> rulesForProduct) {
        return evaluate(selectedProductIds, rulesForProduct, null);
    }

    /**
     * Evaluate rules for the given selected product IDs.
     *
     * @param selectedProductIds initial selection (may be null/empty)
     * @param rulesForProduct    loads rules for a source product id
     * @param attributes         optional selection attribute bag ({@code key → value});
     *                           null/empty skips {@code conditionExpression} evaluation
     */
    public static ConfigurationValidationResult evaluate(
            List<Long> selectedProductIds,
            Function<Long, List<ProductConfigurationRuleDto>> rulesForProduct,
            Map<String, String> attributes) {
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
                case VALIDATES -> evaluateValidates(source, rule, working, attributes, violations);
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
            Map<String, String> attributes,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && !productSet.contains(targetId)) {
            violations.add(violation(rule, VALIDATES,
                    defaultMessage(rule, "Product " + productId + " validation requires product " + targetId),
                    "Add product " + targetId + " required by VALIDATES rule"));
        }

        String expression = rule.getConditionExpression();
        if (expression == null || expression.isBlank()) {
            return;
        }
        if (attributes == null || attributes.isEmpty()) {
            log.debug("Skipping VALIDATES conditionExpression (attrs empty): ruleId={} expr={}",
                    rule.getId(), expression.trim());
            return;
        }

        Boolean matched = matchesAttrExpression(expression.trim(), attributes);
        if (matched == null) {
            log.debug("Skipping unsupported VALIDATES conditionExpression: ruleId={} expr={}",
                    rule.getId(), expression.trim());
            return;
        }
        if (!matched) {
            violations.add(violation(rule, VALIDATES,
                    defaultMessage(rule, "Product " + productId + " failed validation expression "
                            + expression.trim()),
                    "Adjust selection attributes to satisfy " + expression.trim()));
        }
    }

    /**
     * Evaluate constrained {@code attr:key=value} / {@code attr:key!=value} dialect.
     *
     * @return {@code true}/{@code false} when parsed; {@code null} when unsupported
     */
    static Boolean matchesAttrExpression(String expression, Map<String, String> attributes) {
        if (expression == null || attributes == null) {
            return null;
        }
        String trimmed = expression.trim();
        if (!trimmed.regionMatches(true, 0, ATTR_PREFIX, 0, ATTR_PREFIX.length())) {
            return null;
        }
        String body = trimmed.substring(ATTR_PREFIX.length());
        int ne = body.indexOf("!=");
        int eq = body.indexOf('=');
        final String key;
        final String expected;
        final boolean negate;
        if (ne >= 0 && (eq < 0 || ne < eq)) {
            key = body.substring(0, ne).trim();
            expected = body.substring(ne + 2).trim();
            negate = true;
        } else if (eq >= 0) {
            key = body.substring(0, eq).trim();
            expected = body.substring(eq + 1).trim();
            negate = false;
        } else {
            return null;
        }
        if (key.isEmpty()) {
            return null;
        }
        String actual = attributes.get(key);
        boolean equals = actual != null && actual.equals(expected);
        return negate ? !equals : equals;
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
