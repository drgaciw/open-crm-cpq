package com.opencrm.quote.quote;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.quote.lineitem.QuoteLineItemDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteDto {

    private Long id;
    private String uuid;
    private String quoteNumber;
    private String name;
    private String description;
    private String customerExternalId;
    private String dealExternalId;
    private QuoteStatus status;
    private DealType dealType;
    private String subscriptionType;
    private String currency;
    private String region;
    private int durationInMonths;
    private LocalDate startDate;
    private LocalDate endDate;
    /** ValidityPeriod: the date through which this quote is valid. */
    private LocalDate validUntil;
    private String targetSubscriptionNumber;
    private Long observedRevision;
    private LocalDate subscriptionEffectiveDate;
    /** Null on quotes that are not typed continuations. EXTENSION stays {@link DealType#RENEWAL}. */
    private QuoteContinuationRequest.Operation continuationOperation;
    private BigDecimal subtotal;
    private BigDecimal totalDiscount;
    private BigDecimal totalTax;
    private BigDecimal totalAmount;
    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    private BigDecimal exitArr;
    private boolean rampScheduleStale;
    private com.opencrm.quote.ramp.RampGovernanceDto rampGovernance;
    private ApprovalState approvalState;
    private String approvalTier;
    private String notes;
    private List<QuoteLineItemDto> lineItems;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
