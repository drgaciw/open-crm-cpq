package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteDTO {
    private String externalId;
    private CustomerDTO customer;
    private String quoteName;
    private String subscriptionType;
    private Integer durationInMonths;
    private BigDecimal monthlyPrice;
    private BigDecimal basePrice;
    private BigDecimal finalPrice;
    private String currency;
    private BigDecimal discountRate;
    private BigDecimal priceMultiplier;
    private String status;
    
    // Financial metrics
    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    private BigDecimal clv;
    
    // Additional fields
    private Integer userCount;
    private LocalDate effectiveDate;
    private LocalDate expirationDate;
    private Integer expectedDuration;
    private Boolean isMultiRegion;
    private Boolean hasCustomSupport;
    private BigDecimal churnAdjustment;
    private JsonNode metadata;
    
    private List<QuoteLineItemDTO> lineItems;
}
