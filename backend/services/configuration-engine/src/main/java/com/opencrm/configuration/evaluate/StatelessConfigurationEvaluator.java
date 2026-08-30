package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thin REQUIRES/EXCLUDES evaluator with parity to product-service
 * {@code ConfigurationRuleService.validateConfiguration}. Session
 * {@code ConstraintSolver} remains the placeholder for session APIs (S2B-8 deferred).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatelessConfigurationEvaluator {

    static final String RULE_TYPE_REQUIRES = "REQUIRES";
    static final String RULE_TYPE_EXCLUDES = "EXCLUDES";

    private final ProductConfigurationRuleClient ruleClient;

    public ConfigurationValidationResult evaluate(List<Long> selectedProductIds) {
        if (selectedProductIds == null || selectedProductIds.isEmpty()) {
            return ConfigurationValidationResult.ok();
        }

        Set<Long> productSet = new HashSet<>(selectedProductIds);
        List<ConfigurationViolation> violations = new ArrayList<>();

        for (Long productId : selectedProductIds) {
            List<ProductConfigurationRuleDto> rules = ruleClient.getRulesForProduct(productId);
            for (ProductConfigurationRuleDto rule : rules) {
                if (rule == null || rule.getRuleType() == null) {
                    continue;
                }
                String ruleType = rule.getRuleType().trim().toUpperCase();
                if (RULE_TYPE_REQUIRES.equals(ruleType)) {
                    evaluateRequires(productId, rule, productSet, violations);
                } else if (RULE_TYPE_EXCLUDES.equals(ruleType)) {
                    evaluateExcludes(productId, rule, productSet, violations);
                }
            }
        }

        log.debug("Stateless evaluate for {} products produced {} violations",
                selectedProductIds.size(), violations.size());
        return violations.isEmpty()
                ? ConfigurationValidationResult.ok()
                : ConfigurationValidationResult.invalid(violations);
    }

    private static void evaluateRequires(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && !productSet.contains(targetId)) {
            String description = rule.getErrorMessage() != null
                    ? rule.getErrorMessage()
                    : "Product " + productId + " requires product " + targetId;
            violations.add(ConfigurationViolation.builder()
                    .ruleId(rule.getId())
                    .ruleType(RULE_TYPE_REQUIRES)
                    .description(description)
                    .remediation("Add required product " + targetId + " to the selection")
                    .build());
        }
    }

    private static void evaluateExcludes(
            Long productId,
            ProductConfigurationRuleDto rule,
            Set<Long> productSet,
            List<ConfigurationViolation> violations) {
        Long targetId = rule.getTargetProductId();
        if (targetId != null && productSet.contains(targetId)) {
            String description = rule.getErrorMessage() != null
                    ? rule.getErrorMessage()
                    : "Product " + productId + " is incompatible with product " + targetId;
            violations.add(ConfigurationViolation.builder()
                    .ruleId(rule.getId())
                    .ruleType(RULE_TYPE_EXCLUDES)
                    .description(description)
                    .remediation("Remove product " + targetId + " or product " + productId + " from the selection")
                    .build());
        }
    }
}
