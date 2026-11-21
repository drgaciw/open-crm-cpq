package com.opencrm.ai.experiment;

import com.opencrm.ai.experiment.entity.RuleExperiment;
import com.opencrm.ai.experiment.entity.RuleExperimentVariant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExperimentResponse(
        boolean success,
        UUID experimentId,
        String name,
        String description,
        Long ruleId,
        String ruleName,
        Long ruleSetId,
        RuleExperimentStatus status,
        double controlTrafficPct,
        UUID winningVariantId,
        String conclusion,
        Long appliedDraftRuleId,
        Instant appliedAt,
        String appliedBy,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<VariantResponse> variants,
        String errorMessage
) {
    public static ExperimentResponse from(RuleExperiment experiment, List<RuleExperimentVariant> variants) {
        List<VariantResponse> variantResponses = variants == null
                ? List.of()
                : variants.stream().map(VariantResponse::from).toList();
        return new ExperimentResponse(
                true,
                experiment.getId(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getRuleId(),
                experiment.getRuleName(),
                experiment.getRuleSetId(),
                experiment.getStatus(),
                experiment.getControlTrafficPct(),
                experiment.getWinningVariantId(),
                experiment.getConclusion(),
                experiment.getAppliedDraftRuleId(),
                experiment.getAppliedAt(),
                experiment.getAppliedBy(),
                experiment.getCreatedBy(),
                experiment.getCreatedAt(),
                experiment.getStartedAt(),
                experiment.getCompletedAt(),
                variantResponses,
                null);
    }

    public static ExperimentResponse failure(String errorMessage) {
        return new ExperimentResponse(
                false, null, null, null, null, null, null, null, 0,
                null, null, null, null, null, null, null, null, null, List.of(), errorMessage);
    }

    public record VariantResponse(
            UUID variantId,
            String variantName,
            String description,
            String ruleContent,
            boolean controlVariant,
            String status,
            double overallScore,
            double revenueImpact,
            double conversionRateImpact,
            double averageExecutionMs,
            int sampleCount,
            double averageDiscount,
            double totalRevenue,
            double customerSatisfaction,
            String metricsJson
    ) {
        public static VariantResponse from(RuleExperimentVariant variant) {
            return new VariantResponse(
                    variant.getId(),
                    variant.getVariantName(),
                    variant.getDescription(),
                    variant.getRuleContent(),
                    variant.isControlVariant(),
                    variant.getStatus(),
                    variant.getOverallScore(),
                    variant.getRevenueImpact(),
                    variant.getConversionRateImpact(),
                    variant.getAverageExecutionMs(),
                    variant.getSampleCount(),
                    variant.getAverageDiscount(),
                    variant.getTotalRevenue(),
                    variant.getCustomerSatisfaction(),
                    variant.getMetricsJson());
        }
    }
}
