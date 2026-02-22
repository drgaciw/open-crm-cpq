package com.opencrm.rules.engine;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Standard priority bands for rule execution ordering.
 * Higher priority values execute first.
 */
@Getter
@RequiredArgsConstructor
public enum RulePriority {
    POLICY_GUARDRAIL(900, 1000),
    CONFIG_VALIDATION(700, 899),
    LINE_PRICING(500, 699),
    QUOTE_ADJUSTMENT(300, 499),
    APPROVAL_WORKFLOW(200, 299),
    EXPLAINABILITY(100, 199);

    private final int minPriority;
    private final int maxPriority;
}
