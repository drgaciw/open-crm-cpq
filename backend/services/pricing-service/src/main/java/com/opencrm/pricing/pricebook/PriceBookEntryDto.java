package com.opencrm.pricing.pricebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for PriceBookEntry entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PriceBookEntryDto {

    private Long id;
    private String uuid;
    private Long priceBookId;
    private String productExternalId;
    private BigDecimal listPrice;
    private BigDecimal floorPrice;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
