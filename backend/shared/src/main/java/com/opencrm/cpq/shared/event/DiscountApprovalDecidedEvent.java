package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.time.Instant;

public record DiscountApprovalDecidedEvent(
        String approvalId,
        String quoteId,
        String lineId,
        String decision,
        String decidedBy,
        Instant decidedAt,
        String reason
) implements Serializable {
}
