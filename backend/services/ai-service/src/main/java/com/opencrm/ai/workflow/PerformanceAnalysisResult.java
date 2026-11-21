package com.opencrm.ai.workflow;

import java.util.List;
import java.util.Map;

/**
 * Advisory performance analysis for a pricing rule.
 */
public record PerformanceAnalysisResult(
        boolean success,
        Long ruleId,
        String agentOutput,
        List<String> issues,
        List<String> recommendations,
        Map<String, Object> impact,
        String errorMessage
) {
    public static PerformanceAnalysisResult failure(Long ruleId, String error) {
        return new PerformanceAnalysisResult(false, ruleId, null, List.of(), List.of(), Map.of(), error);
    }
}
