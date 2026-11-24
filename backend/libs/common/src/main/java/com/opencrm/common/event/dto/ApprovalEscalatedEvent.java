package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DRG-444 / BC-04-04. The SLA window elapsed and the request followed its escalation path.
 * Topic: {@code cpq.approval.escalated}. Publisher: approval-routing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalEscalatedEvent(
        Long approvalId,
        String quoteId,
        String fromTier,
        String toTier,
        Instant escalatedAt,
        Instant slaDeadline) {
}
