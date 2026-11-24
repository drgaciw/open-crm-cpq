package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Emitted by approval-routing when a quote version is evaluated against commercial policy
 * (BC-04-02, DRG-442). {@code verdict} is Pass, RequiresApproval, or Blocked. Each breach reports the
 * quote's actual value against the policy limit. The same evaluation is appended to the BC-20 audit trail.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuoteEvaluatedEvent(
        String quoteId,
        Long quoteVersion,
        String tenantId,
        String actorId,
        String verdict,
        List<Long> policyIds,
        List<BreachExplained> breaches) {

    /** One breached threshold: the actual quote value and the policy limit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BreachExplained(
            Long policyId,
            Integer policyVersion,
            String thresholdType,
            String actual,
            String limit) {
    }
}
