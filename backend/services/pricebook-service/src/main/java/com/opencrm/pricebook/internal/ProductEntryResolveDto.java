package com.opencrm.pricebook.internal;

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
    private Long priceBookEntryId;
    private Long priceBookEntryVersion;
    private BigDecimal listPrice;
    private BigDecimal floorPrice;
    private String currency;
}
