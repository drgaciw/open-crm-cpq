package com.opencrm.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Recorded exception at the API boundary. The JPA entity stays inside the service (ADR-006). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyExceptionDto {

    private Long id;
    private String quoteId;
    private Long quoteVersion;
    private Long policyId;
    private String justification;
    private Instant expiresAt;
    private String recordedBy;
}
