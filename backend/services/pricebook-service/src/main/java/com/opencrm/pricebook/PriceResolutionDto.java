package com.opencrm.pricebook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DRG-431 / BC-03-01: the result of GetListPrice or GetCost — the amount resolved for a SKU, currency and
 * unit of measure as of a given date, from either a price book (list price) or a linked cost book (cost).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PriceResolutionDto {

    private Long bookId;
    private String sku;
    private String currency;
    private String unitOfMeasure;
    private LocalDate asOfDate;
    private boolean matched;
    private BigDecimal amount;
}
