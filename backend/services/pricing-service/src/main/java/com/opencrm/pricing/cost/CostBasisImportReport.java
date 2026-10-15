package com.opencrm.pricing.cost;

import java.util.List;

/**
 * The response to a cost export ingestion (ADR-006 DTO): its audit entry, every rejected row, and a plain statement of
 * how far the billing unit was checked.
 *
 * @param billingUnitCheck the billing unit is checked only where the catalog metadata carries {@code billingUnit}
 *                         (partial F-2 coverage, review finding M5); this says so and counts the accepted rows that were
 *                         not unit-checked
 */
public record CostBasisImportReport(CostBasisImportSummary audit, List<RowRejection> rejections,
                                    String billingUnitCheck) {

    public CostBasisImportReport {
        rejections = List.copyOf(rejections);
    }

    public CostBasisImportReport(CostBasisImportSummary audit, List<RowRejection> rejections) {
        this(audit, rejections, billingUnitCheck(audit.unitUnchecked()));
    }

    static String billingUnitCheck(int unitUnchecked) {
        return "The billing unit is checked against the catalog only where the catalog metadata carries billingUnit; "
                + unitUnchecked + (unitUnchecked == 1 ? " accepted row was" : " accepted rows were")
                + " loaded without a billing unit check.";
    }
}
