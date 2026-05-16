package com.opencrm.common.event.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versioned published-language contract for {@code cpq.quote.status.accepted}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAcceptedEventV1 {

    public static final int SCHEMA_VERSION = 1;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;

    private String quoteId;
    private Instant acceptedAt;
    private String customerId;
    private BigDecimal totalAmount;
    private String currencyCode;
}
