package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when a deal reaches WON or LOST.
 * Topic: {@code cpq.crm.deal.closed}.
 *
 * <p>Pipeline state only: outcome, the stage it left, and an optional loss reason. No amount, currency,
 * discount, price, or approval decision. Those stay on pricing and approval events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealClosedEvent {

    private String dealId;
    private String organizationId;
    private String outcome;
    private String previousStage;
    private Instant closedAt;
    private String lostReason;
}
