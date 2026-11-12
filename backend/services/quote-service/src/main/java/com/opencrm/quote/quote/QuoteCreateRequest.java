package com.opencrm.quote.quote;

import jakarta.validation.constraints.NotBlank;
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
public class QuoteCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotBlank(message = "Customer external ID is required")
    private String customerExternalId;

    private String dealExternalId;

    /**
     * CRM opportunity type ({@code com.opencrm.crm.deal.DealType} name).
     * When set, {@link CrmQuoteDealTypeMapping} assigns {@link #dealType} during
     * {@link QuoteService#create}. When omitted, {@link #dealType} is stored as given,
     * or {@link DealType#NEW} when that is omitted too.
     */
    private String crmDealType;

    /** Quote deal type. Ignored when {@link #crmDealType} is set, unless the two disagree. */
    private DealType dealType;

    private String subscriptionType;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency;

    @Size(max = 20)
    private String region;

    private Integer durationInMonths;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * ValidityPeriod: the date through which this quote is valid. Optional; {@link QuoteService#create} defaults it
     * to {@link QuoteService#DEFAULT_VALIDITY_DAYS} days from creation when omitted.
     */
    private LocalDate validUntil;

    private String targetSubscriptionNumber;
    private Long observedRevision;
    private LocalDate subscriptionEffectiveDate;

    /**
     * Ignored on create. {@link QuoteContinuationService} is the only writer of the stored operation.
     */
    private QuoteContinuationRequest.Operation continuationOperation;

    private String notes;
}
