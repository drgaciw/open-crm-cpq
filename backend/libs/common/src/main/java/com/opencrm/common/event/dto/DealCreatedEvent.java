package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when a deal is created. Deal is the opportunity, so this is the OpportunityCreated event.
 * Topic: {@code cpq.crm.deal.created}.
 *
 * <p>Identity and opening stage only. Amount, currency, and quote acceptance stay off this payload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealCreatedEvent {

    private String dealId;
    private String organizationId;
    private String name;
    private String stage;
    private Instant createdAt;
}
