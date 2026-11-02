package com.opencrm.crm.territory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic matcher for territory and segment rules.
 *
 * <p>A rule wins only when geography, industry, and size all match. Geography matches the country, and the
 * state when the rule names one. Size is the inclusive employee-count window; a missing bound is open.
 * The organization must have a known non-negative employee count. No matching rule means unassigned.
 *
 * <p>When several rules match, the winner is the lowest priority, then a state-specific rule over a
 * country-wide rule, then the narrower employee window, then the lowest id, then the territory and segment
 * codes. The same inputs always select the same rule.
 */
public final class TerritoryAssignmentRules {

    private static final Comparator<TerritoryAssignmentRule> WINNER = Comparator
            .comparingInt(TerritoryAssignmentRule::getPriority)
            .thenComparingInt(TerritoryAssignmentRules::geographyRank)
            .thenComparingLong(TerritoryAssignmentRules::sizeSpan)
            .thenComparing(TerritoryAssignmentRule::getId, Comparator.nullsLast(Long::compareTo))
            .thenComparing(rule -> normalize(rule.getTerritoryCode()), Comparator.nullsLast(String::compareTo))
            .thenComparing(rule -> normalize(rule.getSegmentCode()), Comparator.nullsLast(String::compareTo));

    private TerritoryAssignmentRules() {
    }

    public static Optional<TerritoryAssignmentRule> select(AssignmentFacts facts, List<TerritoryAssignmentRule> rules) {
        if (facts == null || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        return rules.stream().filter(rule -> matches(facts, rule)).min(WINNER);
    }

    public static boolean matches(AssignmentFacts facts, TerritoryAssignmentRule rule) {
        if (facts == null || rule == null || !rule.isActive() || rule.isDeleted()) {
            return false;
        }
        if (normalize(rule.getCountry()) == null || normalize(rule.getIndustry()) == null) {
            return false;
        }
        if (normalize(rule.getTerritoryCode()) == null || normalize(rule.getSegmentCode()) == null) {
            return false;
        }
        return geographyMatches(facts, rule) && industryMatches(facts, rule) && sizeMatches(facts, rule);
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean geographyMatches(AssignmentFacts facts, TerritoryAssignmentRule rule) {
        if (!equalsFolded(facts.country(), rule.getCountry())) {
            return false;
        }
        String ruleState = normalize(rule.getState());
        if (ruleState == null) {
            return true;
        }
        return equalsFolded(facts.state(), ruleState);
    }

    private static boolean industryMatches(AssignmentFacts facts, TerritoryAssignmentRule rule) {
        return equalsFolded(facts.industry(), rule.getIndustry());
    }

    private static boolean sizeMatches(AssignmentFacts facts, TerritoryAssignmentRule rule) {
        Integer count = facts.employeeCount();
        if (count == null || count < 0) {
            return false;
        }
        Integer min = rule.getMinEmployeeCount();
        Integer max = rule.getMaxEmployeeCount();
        if (min != null && count < min) {
            return false;
        }
        return max == null || count <= max;
    }

    private static boolean equalsFolded(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return normalizedLeft != null && normalizedRight != null && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    /** State-specific rules outrank a country-wide rule. Lower rank wins. */
    private static int geographyRank(TerritoryAssignmentRule rule) {
        return normalize(rule.getState()) == null ? 1 : 0;
    }

    /** Smaller inclusive window wins. An open side is wider than any finite window. */
    private static long sizeSpan(TerritoryAssignmentRule rule) {
        Integer min = rule.getMinEmployeeCount();
        Integer max = rule.getMaxEmployeeCount();
        if (min == null && max == null) {
            return Long.MAX_VALUE;
        }
        if (min == null || max == null) {
            return Long.MAX_VALUE - 1;
        }
        return (long) max - (long) min;
    }
}
