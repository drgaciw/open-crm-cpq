package com.opencrm.quote.approval;

import java.time.Instant;

/** BFF view of a recorded exception. No tenant field. */
public record PolicyExceptionResponse(
        Long id,
        Long quoteId,
        Long quoteVersion,
        Long policyId,
        String justification,
        Instant expiresAt,
        String recordedBy) {
}
