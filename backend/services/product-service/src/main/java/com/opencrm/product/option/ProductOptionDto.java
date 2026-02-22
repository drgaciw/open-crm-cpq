package com.opencrm.product.option;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductOptionDto {

    private Long id;
    private String uuid;
    private Long productId;
    private String name;
    private String description;
    private BigDecimal priceAdjustment;
    private AdjustmentType adjustmentType;
    private boolean defaultOption;
    private int sortOrder;
}
