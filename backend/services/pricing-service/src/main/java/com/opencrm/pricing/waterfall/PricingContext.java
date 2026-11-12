package com.opencrm.pricing.waterfall;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Context for pricing execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingContext {

    private String customerExternalId;
    private String quoteExternalId;
    private List<PricingLineRequest> lineItems;
    private String region;
    private String currency;
    private Integer durationInMonths;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate termStartDate;
    private String subscriptionType; // NEW, AMENDMENT, RENEWAL
    private Map<String, Object> metadata;
}
