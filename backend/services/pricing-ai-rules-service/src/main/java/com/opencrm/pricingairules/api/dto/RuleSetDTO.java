package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleSetDTO {
    private Long id;
    private String name;
    private String description;
    private String version;
    private String status;
    private ZonedDateTime effectiveDate;
    private ZonedDateTime expirationDate;
    private List<RuleDTO> rules;
}
