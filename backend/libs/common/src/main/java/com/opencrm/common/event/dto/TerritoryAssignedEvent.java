package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when an account (organization) is assigned a territory and segment, or that assignment changes.
 * Topic: {@code cpq.crm.account.territory-assigned}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerritoryAssignedEvent {

    private String organizationId;
    private String territory;
    private String segment;
    private Long ruleId;
    private Instant assignedAt;
}
