package com.opencrm.pricing.integration.pricebook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntryResolveDto {

    private boolean entryMatched;
    private BigDecimal listPrice;
    private BigDecimal floorPrice;
    private String currency;
}
