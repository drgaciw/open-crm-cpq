package com.opencrm.pricing.ruletools;

import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Static conflict analysis over stored rule-set DRL (price-dools {@code RuleConflictService}).
 * Does not compile a KieBase or mutate rules.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleConflictAnalyzer {

    private static final Set<String> CATEGORIES = Set.of(
            "discount", "commitment", "pricing", "tier", "risk", "contract", "volume", "churn");

    private final RuleService ruleService;

    public RuleConflictReportDto analyze(Long ruleSetId) {
        List<RuleDto> rules = ruleService.listByRuleSet(ruleSetId, Pageable.unpaged()).getContent();
        List<RuleConflictDto> conflicts = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                RuleDto left = rules.get(i);
                RuleDto right = rules.get(j);
                conflicts.addAll(checkSalienceConflicts(left, right));
                conflicts.addAll(checkRedundantRules(left, right));
                conflicts.addAll(checkOpposingEffects(left, right));
            }
        }

        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (RuleConflictDto conflict : conflicts) {
            typeCounts.merge(conflict.getConflictType(), 1L, Long::sum);
        }

        conflicts.sort(Comparator.comparingInt(RuleConflictDto::getSeverity).reversed());

        return RuleConflictReportDto.builder()
                .ruleSetId(ruleSetId)
                .totalRules(rules.size())
                .totalConflicts(conflicts.size())
                .healthScore(healthScore(conflicts, rules.size()))
                .typeCounts(typeCounts)
                .conflicts(conflicts)
                .build();
    }

    private List<RuleConflictDto> checkSalienceConflicts(RuleDto left, RuleDto right) {
        if (!haveSimilarPatterns(left, right)) {
            return List.of();
        }
        Integer p1 = left.getPriority();
        Integer p2 = right.getPriority();
        if (p1 != null && p1.equals(p2)) {
            return List.of();
        }
        return List.of(conflict(left, right, "SALIENCE_CONFLICT",
                "Rules have similar patterns with different priority, which may cause unexpected interactions",
                7,
                List.of(
                        "Review and adjust the rules to ensure desired execution order",
                        "Consider merging the rules if they perform similar actions",
                        "Add more specific constraints to differentiate the rules")));
    }

    private List<RuleConflictDto> checkRedundantRules(RuleDto left, RuleDto right) {
        if (!haveSimilarNames(left.getName(), right.getName()) || !haveSimilarPatterns(left, right)) {
            return List.of();
        }
        return List.of(conflict(left, right, "REDUNDANT_RULES",
                "Rules appear to be redundant with similar patterns and names",
                5,
                List.of(
                        "Consider merging the rules into a single rule",
                        "Add documentation to clarify the intended difference",
                        "Rename one of the rules to better reflect its purpose")));
    }

    private List<RuleConflictDto> checkOpposingEffects(RuleDto left, RuleDto right) {
        String n1 = haystack(left);
        String n2 = haystack(right);
        boolean leftUp = increases(n1);
        boolean leftDown = decreases(n1);
        boolean rightUp = increases(n2);
        boolean rightDown = decreases(n2);
        if (!((leftUp && rightDown) || (leftDown && rightUp))) {
            return List.of();
        }
        if (!haveSimilarPatterns(left, right)) {
            return List.of();
        }
        return List.of(conflict(left, right, "OPPOSING_EFFECTS",
                "Rules appear to have opposing effects that might cancel each other",
                8,
                List.of(
                        "Review the business logic to ensure both rules are needed",
                        "Add constraints so they apply to different scenarios",
                        "Adjust priority to ensure a specific execution order if both are needed")));
    }

    private boolean haveSimilarPatterns(RuleDto left, RuleDto right) {
        String n1 = haystack(left);
        String n2 = haystack(right);
        for (String category : CATEGORIES) {
            if (n1.contains(category) && n2.contains(category)) {
                return true;
            }
        }
        return false;
    }

    private boolean haveSimilarNames(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        String a = name1.toLowerCase(Locale.ROOT);
        String b = name2.toLowerCase(Locale.ROOT);
        int threshold = Math.min(a.length(), b.length()) / 3;
        return levenshtein(a, b) <= threshold;
    }

    private static String haystack(RuleDto rule) {
        String name = rule.getName() == null ? "" : rule.getName();
        String content = rule.getContent() == null ? "" : rule.getContent();
        return (name + " " + content).toLowerCase(Locale.ROOT);
    }

    private static boolean increases(String haystack) {
        return haystack.contains("increase") || haystack.contains("premium") || haystack.contains("surcharge")
                || haystack.contains("add");
    }

    private static boolean decreases(String haystack) {
        return haystack.contains("decrease") || haystack.contains("discount") || haystack.contains("reduce");
    }

    private static RuleConflictDto conflict(
            RuleDto left,
            RuleDto right,
            String type,
            String description,
            int severity,
            List<String> resolutions) {
        return RuleConflictDto.builder()
                .rule1Id(left.getId())
                .rule1Name(left.getName())
                .rule2Id(right.getId())
                .rule2Name(right.getName())
                .conflictType(type)
                .description(description)
                .severity(severity)
                .resolutionOptions(resolutions)
                .build();
    }

    private static int healthScore(List<RuleConflictDto> conflicts, int totalRules) {
        if (conflicts.isEmpty()) {
            return 100;
        }
        double totalSeverity = conflicts.stream().mapToInt(RuleConflictDto::getSeverity).sum();
        int divisor = Math.max(totalRules, 1) * 2;
        double score = 100 - (totalSeverity / divisor);
        return (int) Math.max(0, Math.min(100, score));
    }

    private static int levenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}
