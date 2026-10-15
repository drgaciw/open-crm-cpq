package com.opencrm.pricing.cost;

/**
 * A rejected row of a cost export, as reported to the caller (ADR-006 DTO).
 *
 * @param line              1-based line of the file where the record starts (the header is line 1)
 * @param productExternalId the row's product id as written, or {@code null} when the row could not be split
 * @param component         the row's component as written, or {@code null}
 * @param reason            why it was rejected
 * @param detail            a human-readable explanation
 */
public record RowRejection(int line, String productExternalId, String component, RejectionReason reason,
                           String detail) {

    static RowRejection of(CostExportRow row, RejectionReason reason, String detail) {
        return new RowRejection(row.line(), row.productExternalId(), row.component().name(), reason, detail);
    }
}
