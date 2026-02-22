package com.opencrm.product.bundle;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductBundleDto {

    private Long id;
    private String uuid;
    private Long productId;
    private String name;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer minItems;
    private Integer maxItems;
    private String status;
    private List<BundleItemDto> items;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
