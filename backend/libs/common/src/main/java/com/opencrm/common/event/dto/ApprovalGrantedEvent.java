package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DRG-444 / BC-04-04. An approver granted a pending approval.
 * Topic: {@code cpq.approval.granted}. Publisher: approval-routing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalGrantedEvent(
        Long approvalId,
        String quoteId,
        String approverId,
        String approvalTier,
        String comment,
        Instant decidedAt) {
}
