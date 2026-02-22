package com.opencrm.product.attribute;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValueSetRequest {

    @NotNull(message = "Attribute ID is required")
    private Long attributeId;

    private String value;
}
