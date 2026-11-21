package com.opencrm.pricing.ruletools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One static conflict between two stored rules (PD1-1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConflictDto {

    private Long rule1Id;
    private String rule1Name;
    private Long rule2Id;
    private String rule2Name;
    private String conflictType;
    private String description;
    private int severity;
    @Builder.Default
    private List<String> resolutionOptions = new ArrayList<>();
}
