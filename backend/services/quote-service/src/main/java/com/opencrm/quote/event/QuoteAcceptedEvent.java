package com.opencrm.quote.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAcceptedEvent {

    private String quoteNumber;
    private String quoteUuid;
    private String customerExternalId;
    private String dealExternalId;
    private String dealType;
    private BigDecimal totalAmount;
    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    private String currency;
    private int durationInMonths;
    private Instant acceptedAt;
}
