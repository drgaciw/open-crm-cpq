package com.opencrm.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Structured configuration validate payload for {@code POST /api/v1/configuration/validate}
 * (S2B-11 / ADR-configuration-api-reconciliation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationValidationResult {

    private boolean valid;

    @Builder.Default
    private List<ConfigurationViolation> violations = Collections.emptyList();

    public static ConfigurationValidationResult ok() {
        return ConfigurationValidationResult.builder()
                .valid(true)
                .violations(List.of())
                .build();
    }

    public static ConfigurationValidationResult invalid(List<ConfigurationViolation> violations) {
        List<ConfigurationViolation> list = violations == null ? List.of() : List.copyOf(violations);
        return ConfigurationValidationResult.builder()
                .valid(list.isEmpty())
                .violations(list)
                .build();
    }

    /**
     * Build a result where only hard rule types fail {@code valid}.
     * Soft types (e.g. RECOMMENDS) may appear in {@code violations} while {@code valid} stays true.
     */
    public static ConfigurationValidationResult of(List<ConfigurationViolation> violations) {
        List<ConfigurationViolation> list = violations == null ? List.of() : List.copyOf(violations);
        boolean hardFailure = list.stream().anyMatch(ConfigurationValidationResult::isHardViolation);
        return ConfigurationValidationResult.builder()
                .valid(!hardFailure)
                .violations(list)
                .build();
    }

    private static boolean isHardViolation(ConfigurationViolation violation) {
        if (violation == null || violation.getRuleType() == null) {
            return false;
        }
        String type = violation.getRuleType().trim().toUpperCase();
        return "REQUIRES".equals(type) || "EXCLUDES".equals(type) || "VALIDATES".equals(type);
    }
}
