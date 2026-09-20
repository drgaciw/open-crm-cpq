package com.opencrm.common.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Immutable commercial facts captured at quote acceptance. Null facts remain missing evidence. */
public record AcceptedOrderSnapshot(int schemaVersion, String snapshotId, String quoteId,
        String quoteNumber, int acceptedRevision, String scopeId, String customerId, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant acceptedAt,
        String currency, String subtotal, String discount, String tax, String total, String address,
        String approvalReference, String taxEvidence, String configurationReference, List<Line> lines) {
    public AcceptedOrderSnapshot {
        lines = List.copyOf(lines);
    }
    public record Line(String sourceLineId, String productId, String description, String fulfillmentType,
            String chargeType, int quantity, String uom, String unitPrice, String discount, String tax,
            String total, String billingFrequency, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalDate startDate, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) LocalDate endDate) {}
}
