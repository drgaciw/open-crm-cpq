package com.opencrm.product.bundle;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BundleItemDto {

    private Long id;
    private String uuid;
    private Long productId;
    private int quantity;
    private boolean optional;
    private int sortOrder;
}
