package com.opencrm.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.approval.domain.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * API-boundary DTO for approval-routing {@code CommercialPolicy}. Entity {@code deleted} is intentionally omitted.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommercialPolicyDto {

    private Long id;
    private String uuid;
    private String name;
    private String description;
    private PolicyStatus status;
    private Integer policyVersion;
    private Long supersedesId;
    private String segment;
    private String productFamily;
    private String region;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private List<PolicyThresholdDto> thresholds;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
