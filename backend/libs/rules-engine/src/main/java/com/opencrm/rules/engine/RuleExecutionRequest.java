package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for executing business-user and programmatic rules through one port.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionRequest {

    private RuleExecutionContext context;

    @Builder.Default
    private List<RuleDefinition> rules = new ArrayList<>();
}
