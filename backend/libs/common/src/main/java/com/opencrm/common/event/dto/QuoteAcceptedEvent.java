package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when a quote is accepted.
 * Topic: cpq.quote.status.accepted
 * Publisher: Quote Management
 * Subscribers: Document Generation, Contract/Asset Management
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAcceptedEvent {
    private String quoteId;
    private Instant acceptedAt;
    private String customerId;
    private BigDecimal totalAmount;
    private String currencyCode;
}
