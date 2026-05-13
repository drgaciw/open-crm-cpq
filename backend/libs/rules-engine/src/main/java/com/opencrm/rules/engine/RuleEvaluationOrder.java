package com.opencrm.rules.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces a stable rule evaluation order and validates declared dependencies.
 */
public final class RuleEvaluationOrder {

    public static final String IMPACT_CLASS_METADATA_KEY = "impactClass";
    public static final String DEPENDS_ON_METADATA_KEY = "dependsOn";

    private static final Comparator<RuleDefinition> DETERMINISTIC_ORDER = Comparator
            .comparingInt(RuleDefinition::getPriority).reversed()
            .thenComparingInt(RuleEvaluationOrder::impactRank)
            .thenComparing(RuleDefinition::getId, Comparator.nullsLast(String::compareTo));

    private static final Map<String, Integer> IMPACT_CLASS_RANKS = Map.of(
            "GUARDRAIL", 0,
            "CONFIGURATION", 1,
            "PRICING", 2,
            "APPROVAL", 3,
            "EXPLANATION", 4
    );
    private static final Pattern SALIENCE_LINE = Pattern.compile("(?m)^\\s*salience\\s+[-]?\\d+\\s*$");
    private static final Pattern RULE_LINE = Pattern.compile("(?m)\\brule\\s+");

    private RuleEvaluationOrder() {
    }

    public static List<RuleDefinition> orderAndValidate(List<RuleDefinition> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }

        Map<String, RuleDefinition> rulesById = new LinkedHashMap<>();
        for (RuleDefinition rule : rules) {
            if (rule.getId() == null || rule.getId().isBlank()) {
                throw new IllegalArgumentException("Rule ID is required for deterministic evaluation order");
            }
            if (rulesById.put(rule.getId(), rule) != null) {
                throw new IllegalArgumentException("Duplicate rule ID " + rule.getId());
            }
        }

        Map<String, List<String>> dependenciesByRuleId = new HashMap<>();
        for (RuleDefinition rule : rules) {
            List<String> dependencies = dependencies(rule);
            for (String dependency : dependencies) {
                if (!rulesById.containsKey(dependency)) {
                    throw new IllegalArgumentException("Rule " + rule.getId()
                            + " depends on unknown rule " + dependency);
                }
            }
            dependenciesByRuleId.put(rule.getId(), dependencies);
        }

        List<RuleDefinition> orderedCandidates = rules.stream()
                .sorted(DETERMINISTIC_ORDER)
                .toList();
        List<RuleDefinition> ordered = new ArrayList<>(rules.size());
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (RuleDefinition rule : orderedCandidates) {
            visit(rule, rulesById, dependenciesByRuleId, visiting, visited, ordered);
        }

        return List.copyOf(ordered);
    }

    public static List<RuleDefinition> withDeterministicSalience(List<RuleDefinition> orderedRules) {
        if (orderedRules == null || orderedRules.isEmpty()) {
            return List.of();
        }

        List<RuleDefinition> rulesWithSalience = new ArrayList<>(orderedRules.size());
        for (int i = 0; i < orderedRules.size(); i++) {
            RuleDefinition rule = orderedRules.get(i);
            int salience = orderedRules.size() - i;
            rulesWithSalience.add(RuleDefinition.builder()
                    .id(rule.getId())
                    .name(rule.getName())
                    .description(rule.getDescription())
                    .content(applySalience(rule.getContent(), salience))
                    .priority(rule.getPriority())
                    .ruleType(rule.getRuleType())
                    .engineType(rule.getEngineType())
                    .conditionGroup(rule.getConditionGroup())
                    .actionSet(rule.getActionSet())
                    .conflictPolicy(rule.getConflictPolicy())
                    .explanationCode(rule.getExplanationCode())
                    .metadata(rule.getMetadata())
                    .build());
        }
        return List.copyOf(rulesWithSalience);
    }

    private static void visit(
            RuleDefinition rule,
            Map<String, RuleDefinition> rulesById,
            Map<String, List<String>> dependenciesByRuleId,
            Set<String> visiting,
            Set<String> visited,
            List<RuleDefinition> ordered) {
        if (visited.contains(rule.getId())) {
            return;
        }
        if (!visiting.add(rule.getId())) {
            throw new IllegalArgumentException("Dependency cycle detected at rule " + rule.getId());
        }

        List<RuleDefinition> dependencies = dependenciesByRuleId.getOrDefault(rule.getId(), List.of())
                .stream()
                .map(rulesById::get)
                .sorted(DETERMINISTIC_ORDER)
                .toList();
        for (RuleDefinition dependency : dependencies) {
            visit(dependency, rulesById, dependenciesByRuleId, visiting, visited, ordered);
        }

        visiting.remove(rule.getId());
        visited.add(rule.getId());
        ordered.add(rule);
    }

    private static List<String> dependencies(RuleDefinition rule) {
        String dependsOn = metadataValue(rule, DEPENDS_ON_METADATA_KEY);
        if (dependsOn == null || dependsOn.isBlank()) {
            return List.of();
        }

        List<String> dependencies = new ArrayList<>();
        for (String dependency : dependsOn.split(",")) {
            String trimmed = dependency.trim();
            if (!trimmed.isEmpty()) {
                dependencies.add(trimmed);
            }
        }
        return List.copyOf(dependencies);
    }

    private static int impactRank(RuleDefinition rule) {
        String impactClass = metadataValue(rule, IMPACT_CLASS_METADATA_KEY);
        if (impactClass == null || impactClass.isBlank()) {
            impactClass = rule.getRuleType();
        }

        String normalized = impactClass == null
                ? ""
                : impactClass.trim().toUpperCase(Locale.ROOT);
        return IMPACT_CLASS_RANKS.getOrDefault(normalized, Integer.MAX_VALUE);
    }

    private static String metadataValue(RuleDefinition rule, String key) {
        Map<String, String> metadata = rule.getMetadata();
        return metadata == null ? null : metadata.get(key);
    }

    private static String applySalience(String content, int salience) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String salienceLine = "salience " + salience;
        Matcher existingSalience = SALIENCE_LINE.matcher(content);
        if (existingSalience.find()) {
            return existingSalience.replaceFirst(salienceLine);
        }

        Matcher ruleLine = RULE_LINE.matcher(content);
        if (!ruleLine.find()) {
            return content;
        }

        int lineEnd = content.indexOf('\n', ruleLine.start());
        if (lineEnd < 0) {
            return content + System.lineSeparator() + salienceLine;
        }
        return content.substring(0, lineEnd + 1)
                + salienceLine
                + System.lineSeparator()
                + content.substring(lineEnd + 1);
    }
}
