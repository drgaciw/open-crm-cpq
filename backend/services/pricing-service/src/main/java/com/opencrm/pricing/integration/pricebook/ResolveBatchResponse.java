package com.opencrm.pricing.integration.pricebook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveBatchResponse {

    private Long priceBookId;
    private Long priceBookVersion;
    private String priceBookCurrency;
    private Map<String, ProductEntryResolveDto> entries;
}
