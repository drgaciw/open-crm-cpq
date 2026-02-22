package com.opencrm.quote.lineitem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteLineItemDto {

    private Long id;
    private String uuid;
    private String productExternalId;
    private String productName;
    private String productType;
    private String lineItemType;
    private int quantity;
    private String unitOfMeasure;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal discountAmount;
    private BigDecimal netPrice;
    private BigDecimal extendedAmount;
    private String billingFrequency;
    private int sortOrder;
    private String pricingExplanationCode;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
