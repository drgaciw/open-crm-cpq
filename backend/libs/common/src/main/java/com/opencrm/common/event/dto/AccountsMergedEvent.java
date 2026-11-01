package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published once when two organizations are merged (DRG-484 / BC-12-04).
 * Topic: {@code cpq.crm.account.merged}.
 *
 * <p>{@code survivorId} and {@code mergedAwayId} are the public account UUIDs
 * ({@link com.opencrm.common.valueobject.AccountId}), not CRM numeric row ids.
 * {@code decidedBy} is the actor who chose the survivor ({@code user:<oid>} or
 * {@code service:<name>}). Quote, contract, and other services repoint their own
 * references by consuming this event. CRM does not write their databases.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountsMergedEvent {

    private String survivorId;
    private String mergedAwayId;
    private String decidedBy;
    private Instant mergedAt;
}
