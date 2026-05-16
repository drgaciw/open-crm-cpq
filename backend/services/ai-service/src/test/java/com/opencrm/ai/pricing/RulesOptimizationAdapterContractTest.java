package com.opencrm.ai.pricing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesOptimizationAdapterContractTest {

    @Test
    void adapterExposesOnlyRecommendationQueries() {
        Method[] methods = RulesOptimizationAdapter.class.getDeclaredMethods();

        assertEquals(2, methods.length);
        Set<String> methodNames = Set.of(methods[0].getName(), methods[1].getName());
        assertEquals(Set.of("optimizeRule", "analyzeRule"), methodNames);

        for (String methodName : methodNames) {
            assertFalse(methodName.matches("(?i).*(mutate|apply|publish|activate|save|update|delete|promote).*"),
                    "AI adapter must not expose pricing mutation method: " + methodName);
        }
    }

    @Test
    void optimizationRequestCarriesDraftAndReadOnlyContext() {
        RulesOptimizationRequest request = new RulesOptimizationRequest(
                "quote-discount-cap",
                "rule \"Discount cap\" when then end",
                "Reduce redundant conditions",
                Map.of("quoteType", "NEW_BUSINESS"),
                List.of("discountPolicy", "marginPolicy")
        );

        assertEquals("quote-discount-cap", request.ruleId());
        assertEquals("rule \"Discount cap\" when then end", request.draftRuleContent());
        assertEquals("Reduce redundant conditions", request.optimizationGoal());
        assertEquals(Map.of("quoteType", "NEW_BUSINESS"), request.evaluationContext());
        assertEquals(List.of("discountPolicy", "marginPolicy"), request.policyReferences());
    }

    @Test
    void optimizationResponseReturnsRecommendationWithoutMutationIntent() {
        RulesOptimizationResponse response = new RulesOptimizationResponse(
                "quote-discount-cap",
                "rule \"Discount cap optimized\" when then end",
                List.of("Collapsed duplicate condition"),
                List.of("Requires validation before promotion"),
                false
        );

        assertEquals("quote-discount-cap", response.ruleId());
        assertNotNull(response.optimizedRuleContent());
        assertTrue(response.recommendations().contains("Collapsed duplicate condition"));
        assertTrue(response.warnings().contains("Requires validation before promotion"));
        assertFalse(response.readyForDirectPricingMutation());
    }
}
