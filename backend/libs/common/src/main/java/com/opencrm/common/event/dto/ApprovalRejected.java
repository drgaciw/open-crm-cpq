package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * DRG-444 / BC-04-04. An approver rejected a pending approval. The quote returns to Draft in the
 * quote-service BFF; this event is the routing evidence. Distinct from the older
 * {@link ApprovalRejectedEvent} contract ({@code QuoteApprovalRejected} on
 * {@code cpq.quote.approval.rejected}), which stays published for existing consumers.
 * Topic: {@code cpq.approval.rejected}. Publisher: approval-routing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRejected(
        Long approvalId,
        String quoteId,
        String approverId,
        String approvalTier,
        String comment,
        Instant rejectedAt) {
}
