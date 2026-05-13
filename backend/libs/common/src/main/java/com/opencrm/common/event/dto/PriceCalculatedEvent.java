package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Event published when pricing is calculated for a quote.
 * Topic: cpq.price.calculated
 * Publisher: Pricing Engine
 * Subscribers: Quote Management, Audit and Compliance
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculatedEvent {
    private String quoteId;
    private String customerId;
    private List<String> explanationCodes;
    private String inputSnapshot;
    private String outputSnapshot;
}
