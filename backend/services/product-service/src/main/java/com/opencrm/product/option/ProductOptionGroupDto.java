package com.opencrm.product.option;

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
public class ProductOptionGroupDto {

    private Long id;
    private String uuid;
    private Long productId;
    private String name;
    private String description;
    private SelectionType selectionType;
    private int minSelections;
    private Integer maxSelections;
    private int sortOrder;
    private List<ProductOptionDto> options;
}
