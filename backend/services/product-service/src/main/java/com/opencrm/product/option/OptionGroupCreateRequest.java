package com.opencrm.product.option;

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
public class OptionGroupCreateRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Selection type is required")
    private SelectionType selectionType;

    private int minSelections;

    private Integer maxSelections;

    /**
     * Optional in SPA payloads. Jackson creator-binding rejects a missing primitive {@code int};
     * use {@link Integer} so omitted {@code sortOrder} deserializes as null and maps to entity 0.
     */
    private Integer sortOrder;
}
