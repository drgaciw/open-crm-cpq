package com.opencrm.approval.testdoubles;

import java.time.Duration;
import java.time.Instant;

/**
 * DRG-444 SLA window. Elapsed means {@code now} is at or after {@code openedAt + length}.
 * Feature moves this rule onto the production escalation type. The not-implemented double does not apply it.
 */
public record SlaWindow(Instant openedAt, Duration length) {

    public SlaWindow {
        if (openedAt == null || length == null || length.isZero() || length.isNegative()) {
            throw new IllegalArgumentException("SlaWindow needs an open instant and a positive length");
        }
    }

    public Instant deadline() {
        return openedAt.plus(length);
    }

    public boolean elapsed(Instant now) {
        return !now.isBefore(deadline());
    }
}
