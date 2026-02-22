package com.opencrm.product.attribute;

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
public class AttributeValueDto {

    private Long id;
    private String uuid;
    private Long productId;
    private Long attributeId;
    private String attributeName;
    private String value;
}
