package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Applied recurring rate; effectiveDate is the commercial boundary, not delivery time. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampAppliedEvent {
    private String subscriptionNumber;
    private String acceptedLineUuid;
    private Integer sequence;
    private LocalDate effectiveDate;
    private String currencyCode;
    private BigDecimal quantity;
    private BigDecimal netUnitPrice;
    private BigDecimal netMonthly;
    private BigDecimal arr;
}
