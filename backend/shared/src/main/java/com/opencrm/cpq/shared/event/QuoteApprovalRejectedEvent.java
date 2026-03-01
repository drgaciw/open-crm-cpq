package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.time.Instant;

public record QuoteApprovalRejectedEvent(
        String quoteId,
        Instant rejectedAt,
        String rejectionReason,
        String nextAction
) implements Serializable {
}
