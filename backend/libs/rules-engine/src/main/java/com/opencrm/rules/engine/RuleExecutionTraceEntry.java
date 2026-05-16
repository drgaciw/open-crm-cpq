package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deterministic trace entry for one rule considered by the hybrid port.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionTraceEntry {

    private int sequence;
    private RulesEngineType engineType;
    private String ruleId;
    private String ruleName;
    private int priority;
}
