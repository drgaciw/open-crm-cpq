package com.opencrm.crm.territory;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create request for a geography, industry, and size assignment rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerritoryRuleRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Country is required")
    @Size(max = 255, message = "Country must not exceed 255 characters")
    private String country;

    @Size(max = 255, message = "State must not exceed 255 characters")
    private String state;

    @NotBlank(message = "Industry is required")
    @Size(max = 255, message = "Industry must not exceed 255 characters")
    private String industry;

    @PositiveOrZero(message = "Minimum employee count must be zero or positive")
    private Integer minEmployeeCount;

    @PositiveOrZero(message = "Maximum employee count must be zero or positive")
    private Integer maxEmployeeCount;

    @NotBlank(message = "Territory code is required")
    @Size(max = 255, message = "Territory code must not exceed 255 characters")
    private String territoryCode;

    @NotBlank(message = "Segment code is required")
    @Size(max = 255, message = "Segment code must not exceed 255 characters")
    private String segmentCode;

    /** Null defaults to 100. Lower values win. */
    @PositiveOrZero(message = "Priority must be zero or positive")
    private Integer priority;

    @AssertTrue(message = "minEmployeeCount must not exceed maxEmployeeCount")
    public boolean isEmployeeRangeOrdered() {
        return minEmployeeCount == null || maxEmployeeCount == null || minEmployeeCount <= maxEmployeeCount;
    }
}
