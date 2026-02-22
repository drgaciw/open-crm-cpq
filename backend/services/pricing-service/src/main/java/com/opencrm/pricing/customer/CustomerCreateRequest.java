package com.opencrm.pricing.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a customer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCreateRequest {

    @NotBlank(message = "External ID is required")
    @Size(max = 255, message = "External ID must not exceed 255 characters")
    private String externalId;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 30, message = "Type must not exceed 30 characters")
    private String type;

    private CustomerSegment segment;

    private CustomerRegion region;

    @Size(max = 255, message = "Industry must not exceed 255 characters")
    private String industry;

    private Integer churnScore;

    private BigDecimal annualRevenue;

    private String metadata;
}
