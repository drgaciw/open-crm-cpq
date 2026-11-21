package com.opencrm.pricing.ruletools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-set conflict analysis summary (PD1-1). Advisory; does not mutate rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConflictReportDto {

    private Long ruleSetId;
    private int totalRules;
    private int totalConflicts;
    private int healthScore;
    @Builder.Default
    private Map<String, Long> typeCounts = new LinkedHashMap<>();
    @Builder.Default
    private List<RuleConflictDto> conflicts = new ArrayList<>();
}
