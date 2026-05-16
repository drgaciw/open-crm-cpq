package com.opencrm.ai.pricing;

import java.util.List;

public record RulesAnalysisResponse(
        String ruleId,
        List<String> findings,
        List<String> policyRisks,
        boolean requiresHumanReview
) {
}
