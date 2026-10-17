package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DoD v0.3 item 17. Receipt identity is decided before any expected-version check. */
class AmendmentIdempotencyTest {

    @Test
    void missingReceiptProceedsAndAMatchingDigestReplays() {
        assertThat(AmendmentIdempotency.decide(null, "abc")).isEqualTo(AmendmentIdempotency.Decision.PROCEED);
        assertThat(AmendmentIdempotency.decide("abc", "abc")).isEqualTo(AmendmentIdempotency.Decision.REPLAY);
    }

    @Test
    void sameKeyOrNaturalIdentityWithDifferentContentConflicts() {
        assertThat(AmendmentIdempotency.decide("abc", "def")).isEqualTo(AmendmentIdempotency.Decision.CONFLICT);
    }
}
