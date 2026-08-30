package com.opencrm.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single configuration rule violation for agent-contract validate responses (S2B-11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationViolation {

    /** Persisted rule id when known; may be null for synthetic messages. */
    private Long ruleId;

    /** Rule type code, e.g. REQUIRES, EXCLUDES (string for cross-service stability). */
    private String ruleType;

    /** Human-readable failure description (often the rule errorMessage). */
    private String description;

    /** Optional remediation guidance for agents / UI. */
    private String remediation;
}
