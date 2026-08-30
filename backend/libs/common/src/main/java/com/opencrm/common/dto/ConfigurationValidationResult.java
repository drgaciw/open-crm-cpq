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
}
