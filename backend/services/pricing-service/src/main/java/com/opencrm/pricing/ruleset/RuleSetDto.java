package com.opencrm.pricing.ruleset;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for RuleSet entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleSetDto {

    private Long id;
    private String uuid;
    private String name;
    private String description;
    private RuleSetStatus status;
    private Integer ruleSetVersion;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private String engineType;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
