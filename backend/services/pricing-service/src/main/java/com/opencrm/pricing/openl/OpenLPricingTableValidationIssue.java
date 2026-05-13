package com.opencrm.pricing.openl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One validation issue found while previewing an OpenL table import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLPricingTableValidationIssue {

    private int rowNumber;
    private String column;
    private String code;
    private String message;
}
