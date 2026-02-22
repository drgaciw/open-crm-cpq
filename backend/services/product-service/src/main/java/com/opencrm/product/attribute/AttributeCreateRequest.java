package com.opencrm.product.attribute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

    @NotNull(message = "Data type is required")
    private AttributeDataType dataType;

    private boolean required;

    @Size(max = 500, message = "Default value must not exceed 500 characters")
    private String defaultValue;

    @Size(max = 500, message = "Validation regex must not exceed 500 characters")
    private String validationRegex;

    private String enumValues;

    private int sortOrder;
}
