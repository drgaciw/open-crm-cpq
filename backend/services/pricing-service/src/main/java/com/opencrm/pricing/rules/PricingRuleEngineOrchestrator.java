package com.opencrm.pricing.rules;

import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleEnginePort;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionRequest;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngineType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pricing-service adapter over {@link RuleEnginePort} for hybrid OpenL + Drools execution.
 */
@Service
@RequiredArgsConstructor
public class PricingRuleEngineOrchestrator {

    private final RuleEnginePort ruleEnginePort;

    public RuleExecutionResult execute(
            RuleSet ruleSet,
            List<Rule> activeRules,
            RuleExecutionContext context
    ) {
        List<RuleDefinition> definitions = activeRules.stream()
                .map(rule -> toDefinition(rule, ruleSet))
                .toList();

        return ruleEnginePort.execute(RuleExecutionRequest.builder()
                .context(context)
                .rules(definitions)
                .build());
    }

    RuleDefinition toDefinition(Rule rule, RuleSet ruleSet) {
        String engineType = resolveEngineType(rule, ruleSet);
        return RuleDefinition.builder()
                .id(String.valueOf(rule.getId()))
                .name(rule.getName())
                .description(rule.getDescription())
                .content(rule.getContent())
                .priority(rule.getPriority())
                .ruleType(rule.getRuleType() != null ? rule.getRuleType().name() : null)
                .engineType(engineType)
                .conditionGroup(rule.getConditionGroup())
                .actionSet(rule.getActionSet())
                .conflictPolicy(rule.getConflictPolicy())
                .explanationCode(rule.getExplanationCode())
                .build();
    }

    private String resolveEngineType(Rule rule, RuleSet ruleSet) {
        if (rule.getEngineType() != null && !rule.getEngineType().isBlank()) {
            return rule.getEngineType();
        }
        if (ruleSet != null && ruleSet.getEngineType() != null && !ruleSet.getEngineType().isBlank()) {
            String setEngine = ruleSet.getEngineType();
            if (!"HYBRID".equalsIgnoreCase(setEngine)) {
                return setEngine;
            }
        }
        return RulesEngineType.DROOLS.name();
    }
}
