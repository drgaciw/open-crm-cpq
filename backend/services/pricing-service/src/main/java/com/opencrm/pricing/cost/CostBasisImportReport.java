package com.opencrm.pricing.cost;

import java.util.List;

/** The response to a cost export ingestion (ADR-006 DTO): its audit entry and every rejected row. */
public record CostBasisImportReport(CostBasisImportSummary audit, List<RowRejection> rejections) {

    public CostBasisImportReport {
        rejections = List.copyOf(rejections);
    }
}
