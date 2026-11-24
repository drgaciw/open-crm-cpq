package com.opencrm.approval.domain;

import java.time.Instant;

/**
 * A recorded policy exception {@code EvaluateQuote} may honor (BC-04-05, DRG-445).
 * Bound to one quote id, one quote version and one policy. {@link #expiresAt} is exclusive:
 * an exception whose expiry is not strictly after the evaluation instant does not apply.
 */
public record PolicyExceptionGrant(
        String quoteId,
        long quoteVersion,
        long policyId,
        String tenantId,
        Instant expiresAt) {
}
