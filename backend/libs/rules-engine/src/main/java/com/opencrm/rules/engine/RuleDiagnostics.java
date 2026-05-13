package com.opencrm.rules.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class RuleDiagnostics {

    public RuleDiagnosticReport validatePublication(List<RuleDefinition> rules) {
        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(findDependencyCycles(rules));
        diagnostics.addAll(findConflictingMutations(rules));
        return RuleDiagnosticReport.builder()
                .diagnostics(diagnostics)
                .build();
    }

    public RuleDiagnostic runtimeFailure(String message, List<RuleDefinition> rules) {
        return RuleDiagnostic.builder()
                .severity(RuleDiagnosticSeverity.ERROR)
                .code("RULE_RUNTIME_FAILURE")
                .message(message)
                .ruleIds(rules.stream().map(RuleDefinition::getId).filter(Objects::nonNull).toList())
                .build();
    }

    private List<RuleDiagnostic> findDependencyCycles(List<RuleDefinition> rules) {
        Map<String, Set<String>> dependenciesByRuleId = new HashMap<>();
        Set<String> knownRuleIds = new HashSet<>();
        for (RuleDefinition rule : rules) {
            if (rule.getId() != null) {
                knownRuleIds.add(rule.getId());
            }
        }
        for (RuleDefinition rule : rules) {
            if (rule.getId() == null) {
                continue;
            }
            dependenciesByRuleId.put(rule.getId(), parseDependencies(rule, knownRuleIds));
        }

        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        Set<String> globallyVisited = new HashSet<>();
        for (String ruleId : dependenciesByRuleId.keySet().stream().sorted().toList()) {
            detectCycle(ruleId, dependenciesByRuleId, new ArrayDeque<>(), globallyVisited, diagnostics);
        }
        return diagnostics;
    }

    private void detectCycle(
            String ruleId,
            Map<String, Set<String>> dependenciesByRuleId,
            ArrayDeque<String> path,
            Set<String> globallyVisited,
            List<RuleDiagnostic> diagnostics
    ) {
        if (path.contains(ruleId)) {
            List<String> cycle = new ArrayList<>(path);
            cycle = cycle.subList(cycle.indexOf(ruleId), cycle.size());
            cycle.add(ruleId);
            diagnostics.add(RuleDiagnostic.builder()
                    .severity(RuleDiagnosticSeverity.ERROR)
                    .code("RULE_DEPENDENCY_CYCLE")
                    .message("Rule dependency cycle detected: " + String.join(" -> ", cycle))
                    .ruleIds(cycle)
                    .build());
            return;
        }
        if (!globallyVisited.add(ruleId)) {
            return;
        }

        path.addLast(ruleId);
        for (String dependency : dependenciesByRuleId.getOrDefault(ruleId, Set.of())) {
            detectCycle(dependency, dependenciesByRuleId, path, globallyVisited, diagnostics);
        }
        path.removeLast();
    }

    private Set<String> parseDependencies(RuleDefinition rule, Set<String> knownRuleIds) {
        Set<String> dependencies = new LinkedHashSet<>();
        Map<String, String> metadata = rule.getMetadata();
        if (metadata != null) {
            addCsvValues(dependencies, metadata.get("dependsOn"));
            addCsvValues(dependencies, metadata.get("dependsOnRules"));
        }
        dependencies.removeIf(dependency -> !knownRuleIds.contains(dependency));
        return dependencies;
    }

    private void addCsvValues(Set<String> values, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String value : csv.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
    }

    private List<RuleDiagnostic> findConflictingMutations(List<RuleDefinition> rules) {
        Map<String, List<RuleDefinition>> rulesByMutationKey = new HashMap<>();
        for (RuleDefinition rule : rules) {
            for (String mutationTarget : mutationTargets(rule)) {
                String key = rule.getPriority() + ":" + mutationTarget;
                rulesByMutationKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule);
            }
        }

        List<RuleDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, List<RuleDefinition>> entry : rulesByMutationKey.entrySet()) {
            List<RuleDefinition> conflictingRules = entry.getValue();
            if (conflictingRules.size() < 2) {
                continue;
            }
            conflictingRules.sort(Comparator.comparing(RuleDefinition::getId, Comparator.nullsLast(String::compareTo)));
            String mutationTarget = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
            diagnostics.add(RuleDiagnostic.builder()
                    .severity(RuleDiagnosticSeverity.ERROR)
                    .code("CONFLICTING_RULE_MUTATION")
                    .message("Multiple rules mutate " + mutationTarget + " at priority "
                            + conflictingRules.get(0).getPriority())
                    .ruleIds(conflictingRules.stream()
                            .map(RuleDefinition::getId)
                            .filter(Objects::nonNull)
                            .toList())
                    .build());
        }
        diagnostics.sort(Comparator.comparing(diagnostic -> diagnostic.getRuleIds().toString()));
        return diagnostics;
    }

    private Set<String> mutationTargets(RuleDefinition rule) {
        Set<String> targets = new LinkedHashSet<>();
        Map<String, String> metadata = rule.getMetadata();
        if (metadata != null) {
            addCsvValues(targets, metadata.get("mutates"));
            addCsvValues(targets, metadata.get("mutationTargets"));
        }
        if (rule.getActionSet() != null && !rule.getActionSet().isBlank()) {
            targets.add(rule.getActionSet().trim());
        }
        return targets;
    }
}
