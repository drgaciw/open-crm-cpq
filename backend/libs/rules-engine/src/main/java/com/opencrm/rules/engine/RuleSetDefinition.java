package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a collection of related rules executed together.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSetDefinition {

    private String id;
    private String name;
    private String version;
    private RulesEngineType engineType;

    @Builder.Default
    private List<RuleDefinition> rules = new ArrayList<>();
}
