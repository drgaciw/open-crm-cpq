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
public class ProductAttributeDto {

    private Long id;
    private String uuid;
    private String name;
    private String displayName;
    private AttributeDataType dataType;
    private boolean required;
    private String defaultValue;
    private String validationRegex;
    private String enumValues;
    private int sortOrder;
}
