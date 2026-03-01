package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.math.BigDecimal;

public record DiscountApprovalRequiredEvent(
        String quoteId,
        String lineId,
        BigDecimal marginDelta,
        BigDecimal requestedDiscount
) implements Serializable {
}
