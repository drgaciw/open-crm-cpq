package com.opencrm.rules.engine;

import com.opencrm.rules.model.ExplanationCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of executing a rule set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionResult {

    private boolean success;

    @Builder.Default
    private List<Object> modifiedFacts = new ArrayList<>();

    @Builder.Default
    private List<ExplanationCode> explanations = new ArrayList<>();

    @Builder.Default
    private List<String> firedRuleNames = new ArrayList<>();

    @Builder.Default
    private List<RuleExecutionTraceEntry> trace = new ArrayList<>();

    private int rulesFired;
    private long executionTimeMs;

    @Builder.Default
    private Map<String, Object> outputs = new HashMap<>();

    private String errorMessage;
}
