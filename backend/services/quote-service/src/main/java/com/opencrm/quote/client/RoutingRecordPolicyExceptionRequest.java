package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Body the quote BFF sends to approval-routing. No actor field: the relayed token is the actor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRecordPolicyExceptionRequest {

    private String quoteId;
    private Long quoteVersion;
    private Long policyId;
    private String justification;
    private Instant expiresAt;
    /** The quote row's tenant, so approval-routing can refuse a mismatched caller. */
    private String tenantId;
}
