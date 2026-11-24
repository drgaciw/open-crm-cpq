package com.opencrm.quote.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingPolicyExceptionDto {

    private Long id;
    private String quoteId;
    private Long quoteVersion;
    private Long policyId;
    private String justification;
    private Instant expiresAt;
    private String recordedBy;
}
