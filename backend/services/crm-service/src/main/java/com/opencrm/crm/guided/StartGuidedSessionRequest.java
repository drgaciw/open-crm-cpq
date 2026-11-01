package com.opencrm.crm.guided;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Starts a guided selling session. The seller is taken from the verified token.
 * {@code dealId} is an optional opportunity reference and is not used to close the deal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartGuidedSessionRequest {

    @Positive(message = "Deal id must be positive")
    private Long dealId;
}
