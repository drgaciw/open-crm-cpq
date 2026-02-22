package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context for executing a rule set with facts and globals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionContext {

    private String ruleSetId;
    private String ruleSetVersion;
    private RulesEngineType engineType;

    @Builder.Default
    private List<Object> facts = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> globals = new HashMap<>();

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
