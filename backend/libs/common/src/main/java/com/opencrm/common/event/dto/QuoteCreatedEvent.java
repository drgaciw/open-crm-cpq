package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Event published when a quote is created from an opportunity or account (DRG-447 / BC-05-01: CreateQuote).
 * Topic: {@code cpq.quote.created}.
 *
 * <p>Identity and the opening commercial envelope only: who the quote is for, what currency it is priced in, and how
 * long it is valid. Pricing totals and line items are not on this payload; they are introduced later in the quote's
 * lifecycle (see {@link QuoteAcceptedEvent} for the accepted snapshot) and are not known yet at creation time.
 *
 * <p>Additive and {@code NON_NULL}: a consumer built against an older copy of this class keeps working when a field
 * is added.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteCreatedEvent {

    private String quoteId;
    private String quoteNumber;

    /** The CRM account the quote is for ({@code Quote.customerExternalId}, an Organization's external id). */
    private String accountId;

    /** The CRM opportunity the quote was created from, when supplied ({@code Quote.dealExternalId}, a Deal's uuid). */
    private String dealExternalId;

    private String currency;
    private LocalDate validUntil;
    private String status;
    private Instant createdAt;
}
