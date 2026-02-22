package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a single rule within a rule set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {

    private String id;
    private String name;
    private String description;
    private String content;
    private int priority;
    private String ruleType;
    private String engineType;
    private String conditionGroup;
    private String actionSet;
    private String conflictPolicy;
    private String explanationCode;

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
