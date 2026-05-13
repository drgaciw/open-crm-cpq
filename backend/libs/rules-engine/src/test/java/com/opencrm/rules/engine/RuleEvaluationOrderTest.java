package com.opencrm.rules.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEvaluationOrderTest {

    @Test
    void ordersRulesByPriorityImpactClassAndRuleId() {
        RuleDefinition approval = rule("rule-020", 800, "APPROVAL");
        RuleDefinition pricingB = rule("rule-010", 800, "PRICING");
        RuleDefinition pricingA = rule("rule-002", 800, "PRICING");
        RuleDefinition lowerPriorityGuardrail = rule("rule-001", 700, "GUARDRAIL");

        List<RuleDefinition> ordered = RuleEvaluationOrder.orderAndValidate(List.of(
                approval,
                pricingB,
                lowerPriorityGuardrail,
                pricingA
        ));

        assertThat(ordered)
                .extracting(RuleDefinition::getId)
                .containsExactly("rule-002", "rule-010", "rule-020", "rule-001");
    }

    @Test
    void honorsDependenciesBeforePriorityTieBreakers() {
        RuleDefinition dependent = rule("rule-001", 900, "PRICING", "rule-002");
        RuleDefinition dependency = rule("rule-002", 800, "EXPLANATION");

        List<RuleDefinition> ordered = RuleEvaluationOrder.orderAndValidate(List.of(dependent, dependency));

        assertThat(ordered)
                .extracting(RuleDefinition::getId)
                .containsExactly("rule-002", "rule-001");
    }

    @Test
    void rejectsUnknownDependencies() {
        RuleDefinition dependent = rule("rule-001", 900, "PRICING", "missing-rule");

        assertThatThrownBy(() -> RuleEvaluationOrder.orderAndValidate(List.of(dependent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depends on unknown rule missing-rule");
    }

    @Test
    void rejectsDependencyCycles() {
        RuleDefinition first = rule("rule-001", 900, "PRICING", "rule-002");
        RuleDefinition second = rule("rule-002", 800, "EXPLANATION", "rule-001");

        assertThatThrownBy(() -> RuleEvaluationOrder.orderAndValidate(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dependency cycle");
    }

    @Test
    void assignsDeterministicSalienceToOrderedRules() {
        RuleDefinition first = rule("rule-001", 900, "PRICING");
        RuleDefinition second = rule("rule-002", 800, "EXPLANATION");

        List<RuleDefinition> rulesWithSalience = RuleEvaluationOrder.withDeterministicSalience(
                List.of(first, second));

        assertThat(rulesWithSalience.get(0).getContent()).contains("salience 2");
        assertThat(rulesWithSalience.get(1).getContent()).contains("salience 1");
    }

    private RuleDefinition rule(String id, int priority, String impactClass) {
        return rule(id, priority, impactClass, null);
    }

    private RuleDefinition rule(String id, int priority, String impactClass, String dependsOn) {
        Map<String, String> metadata = dependsOn == null
                ? Map.of("impactClass", impactClass)
                : Map.of("impactClass", impactClass, "dependsOn", dependsOn);

        return RuleDefinition.builder()
                .id(id)
                .name(id)
                .priority(priority)
                .content("package test; rule \"" + id + "\" when then end")
                .metadata(metadata)
                .build();
    }
}
