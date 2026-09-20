package com.opencrm.crm.lead;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for Lead entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadDto {

    private Long id;
    private String uuid;
    private String title;
    private Long contactId;
    private String contactName;
    private Long organizationId;
    private String organizationName;
    private LeadStatus status;
    private String source;
    private String sourceDetail;
    private BigDecimal estimatedValue;
    private String currency;
    private Integer score;
    /** Advisory only (ADR-015); omitted unless Jev scored the lead. */
    private com.opencrm.crm.lead.scoring.LeadScoreSuggestionDto scoreSuggestion;
    private String description;
    private String ownerId;
    private Instant assignedAt;
    private Instant firstContactedAt;
    private Instant lastContactedAt;
    private Instant qualifiedAt;
    private Instant convertedAt;
    private Long convertedDealId;
    private String lostReason;
    private Instant lostAt;
    private String campaign;
    private LeadPriority priority;
    private String nextAction;
    private Instant nextActionDate;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
