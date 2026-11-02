package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Published when a guided selling session derives an advisory solution (DRG-487 / BC-13-03).
 * Topic: {@code cpq.crm.opportunity.solution-derived}.
 *
 * <p>The selections are pre-selected options for a configuration session. They are advisory:
 * the configuration engine still enforces constraints, and this event does not complete that
 * session or close the opportunity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolutionDerivedEvent {

    private String guidedSessionId;
    private Long dealId;
    private String productId;
    private Map<String, String> selections;
    private Long configurationSessionId;
    private boolean recordedLocally;
    private boolean advisory;
    private Instant derivedAt;
}
