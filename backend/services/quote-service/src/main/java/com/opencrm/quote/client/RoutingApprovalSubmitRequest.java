package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for approval-routing {@code POST /api/v1/approvals} (G3). It names no requester: approval-routing records the
 * caller of the relayed token (ADR-016 decision 6).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingApprovalSubmitRequest {

    private String quoteId;
    /** Quote BFF tier name: DEAL_DESK, SALES_VP, CFO. */
    private String tier;
    private String reason;
}
