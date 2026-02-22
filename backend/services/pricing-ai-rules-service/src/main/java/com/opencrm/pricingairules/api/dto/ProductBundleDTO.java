package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductBundleDTO {
    private Long id;
    private String name;
    private String description;
    private String bundleType;
    private String status;
    private List<ProductBundleItemDTO> bundleItems;
}
