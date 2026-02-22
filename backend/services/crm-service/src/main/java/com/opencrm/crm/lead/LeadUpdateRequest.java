package com.opencrm.crm.lead;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for updating a lead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadUpdateRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private Long contactId;

    private Long organizationId;

    @Size(max = 255, message = "Source must not exceed 255 characters")
    private String source;

    @Size(max = 255, message = "Source detail must not exceed 255 characters")
    private String sourceDetail;

    @PositiveOrZero(message = "Estimated value must be zero or positive")
    private BigDecimal estimatedValue;

    @Size(max = 3, message = "Currency must not exceed 3 characters")
    private String currency;

    private String description;

    private String ownerId;

    @Size(max = 255, message = "Campaign must not exceed 255 characters")
    private String campaign;

    private LeadPriority priority;
}
