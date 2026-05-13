package com.opencrm.pricing.openl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation preview for an OpenL pricing table import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLPricingTableImportResult {

    private Long ruleSetId;
    private String tableName;
    private int validRows;
    private int totalRows;
    private boolean importable;

    @Builder.Default
    private List<OpenLPricingTableValidationIssue> issues = new ArrayList<>();
}
