package com.opencrm.quote.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Seller body for RecordException. Tenant and actor are not fields: the token and the quote row supply them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordQuotePolicyExceptionRequest {

    @NotNull
    private Long policyId;

    @NotNull
    private Long quoteVersion;

    @NotBlank
    @Size(max = 2000)
    private String justification;

    @NotNull
    private Instant expiresAt;
}
