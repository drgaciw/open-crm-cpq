package com.opencrm.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * {@code RecordException}. {@code tenantId} is the quote's own tenant, compared with the token.
 * It is not a caller-chosen approval-row column, and {@code recordedBy} is never accepted here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPolicyExceptionRequest {

    @NotBlank
    private String quoteId;

    @NotNull
    private Long quoteVersion;

    @NotNull
    private Long policyId;

    @NotBlank
    @Size(max = 2000)
    private String justification;

    @NotNull
    private Instant expiresAt;

    private String tenantId;
}
