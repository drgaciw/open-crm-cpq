package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Emitted by approval-routing when {@code SubmitForApproval} creates the requests for one matrix route
 * (BC-04-03, DRG-443). Later serial steps are included with status {@code WAITING}; they are not yet inbox items.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequestedEvent(
        String quoteId,
        String quoteVersion,
        Long matrixId,
        Integer matrixVersion,
        String tenantId,
        List<RequestedStep> steps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestedStep(
            Long approvalRequestId,
            Integer stepOrder,
            String stepMode,
            String approverRole,
            String status,
            String breachTypes) {
    }
}
