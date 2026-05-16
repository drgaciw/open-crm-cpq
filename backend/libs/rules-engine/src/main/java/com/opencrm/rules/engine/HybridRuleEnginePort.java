package com.opencrm.rules.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes OpenL business tables and Drools programmatic rules behind one port.
 */
@Service
@RequiredArgsConstructor
public class HybridRuleEnginePort implements RuleEnginePort {

    private static final List<RulesEngineType> ENGINE_ORDER = List.of(
            RulesEngineType.OPENL_TABLETS,
            RulesEngineType.DROOLS
    );

    private final List<RulesEngine> engines;

    @Override
    public RuleExecutionResult execute(RuleExecutionRequest request) {
        RuleExecutionContext context = request.getContext();
        Map<RulesEngineType, RulesEngine> engineByType = engines.stream()
                .collect(Collectors.toMap(RulesEngine::getEngineType, Function.identity()));
        Map<RulesEngineType, List<RuleDefinition>> rulesByEngine = groupRules(request.getRules());

        RuleExecutionResult combined = RuleExecutionResult.builder()
                .success(true)
                .build();
        List<RuleExecutionTraceEntry> trace = new ArrayList<>();
        int sequence = 1;

        for (RulesEngineType engineType : ENGINE_ORDER) {
            List<RuleDefinition> rules = rulesByEngine.getOrDefault(engineType, List.of());
            if (rules.isEmpty()) {
                continue;
            }

            RulesEngine engine = engineByType.get(engineType);
            if (engine == null) {
                throw new IllegalArgumentException("No rules engine registered for " + engineType);
            }

            RuleExecutionResult result = engine.execute(context, rules);
            combined.setSuccess(combined.isSuccess() && result.isSuccess());
            combined.getModifiedFacts().addAll(result.getModifiedFacts());
            combined.getExplanations().addAll(result.getExplanations());
            combined.getFiredRuleNames().addAll(result.getFiredRuleNames());
            combined.setRulesFired(combined.getRulesFired() + result.getRulesFired());
            combined.setExecutionTimeMs(combined.getExecutionTimeMs() + result.getExecutionTimeMs());
            combined.getOutputs().putAll(result.getOutputs());

            for (RuleDefinition rule : rules) {
                trace.add(RuleExecutionTraceEntry.builder()
                        .sequence(sequence++)
                        .engineType(engineType)
                        .ruleId(rule.getId())
                        .ruleName(rule.getName())
                        .priority(rule.getPriority())
                        .build());
            }
        }

        combined.setTrace(trace);
        return combined;
    }

    private Map<RulesEngineType, List<RuleDefinition>> groupRules(List<RuleDefinition> rules) {
        Map<RulesEngineType, List<RuleDefinition>> rulesByEngine = new EnumMap<>(RulesEngineType.class);
        for (RuleDefinition rule : rules) {
            RulesEngineType engineType = RulesEngineType.valueOf(rule.getEngineType());
            rulesByEngine.computeIfAbsent(engineType, ignored -> new ArrayList<>()).add(rule);
        }
        rulesByEngine.values().forEach(engineRules -> engineRules.sort(
                Comparator.comparingInt(RuleDefinition::getPriority)
                        .thenComparing(RuleDefinition::getId)
        ));
        return rulesByEngine;
    }
}
