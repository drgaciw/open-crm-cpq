package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record QuoteAcceptedEvent(
        String quoteId,
        Instant acceptedAt,
        String customerId,
        BigDecimal totalAmount,
        String currencyCode
) implements Serializable {
}
