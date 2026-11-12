package com.opencrm.quote.quote;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteUpdateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    private String dealExternalId;

    private String subscriptionType;

    @Size(max = 3)
    private String currency;

    @Size(max = 20)
    private String region;

    private Integer durationInMonths;

    private LocalDate startDate;

    private LocalDate endDate;

    /** ValidityPeriod: the date through which this quote is valid. Editable while the quote is DRAFT/REJECTED. */
    private LocalDate validUntil;

    private String targetSubscriptionNumber;
    private Long observedRevision;
    private LocalDate subscriptionEffectiveDate;

    /**
     * Ignored on update. A client value must not replace the server-authored continuation operation.
     */
    private QuoteContinuationRequest.Operation continuationOperation;

    private String notes;
}
