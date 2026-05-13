package com.opencrm.pricing.openl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for preview-validating a business-user maintained OpenL pricing table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLPricingTableImportRequest {

    @NotNull(message = "Rule set ID is required")
    private Long ruleSetId;

    @NotBlank(message = "Table name is required")
    private String tableName;

    @NotBlank(message = "CSV content is required")
    private String csvContent;
}
