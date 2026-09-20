package com.opencrm.quote.snapshot;
import java.time.LocalDate;
import java.util.List;
/** Source evidence captured before approval; no snapshot or identity fields are client writable. */
public record SourceTermsRequest(long expectedVersion, String address, String taxEvidence,
        String configurationReference, List<LineTerms> lines) {
    public record LineTerms(String sourceLineId, String fulfillmentType, String chargeType,
            String tax, LocalDate startDate, LocalDate endDate) {}
}
