package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent/stateless validate path: product rules via {@link ProductConfigurationRuleClient}
 * evaluated by shared {@link ProductRuleEvaluationCore} (Wave G / S2B-8 types).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatelessConfigurationEvaluator {

    private final ProductConfigurationRuleClient ruleClient;

    public ConfigurationValidationResult evaluate(List<Long> selectedProductIds) {
        ConfigurationValidationResult result = ProductRuleEvaluationCore.evaluate(
                selectedProductIds,
                ruleClient::getRulesForProduct);
        log.debug("Stateless evaluate for {} products → valid={} violations={}",
                selectedProductIds == null ? 0 : selectedProductIds.size(),
                result.isValid(),
                result.getViolations() == null ? 0 : result.getViolations().size());
        return result;
    }
}
