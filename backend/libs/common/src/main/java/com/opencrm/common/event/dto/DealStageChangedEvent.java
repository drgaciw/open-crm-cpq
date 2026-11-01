package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Published when a deal moves between open pipeline stages.
 * Topic: {@code cpq.crm.deal.stage.changed}. Closing a deal also emits {@link DealClosedEvent}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealStageChangedEvent {

    private String dealId;
    private String organizationId;
    private String fromStage;
    private String toStage;
    private Instant changedAt;
}
