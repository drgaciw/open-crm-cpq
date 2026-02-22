package com.opencrm.product.attribute;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-attributes")
@RequiredArgsConstructor
@Tag(name = "Product Attributes", description = "Product attribute and value management")
public class ProductAttributeController {

    private final ProductAttributeService attributeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an attribute definition")
    public ApiResponse<ProductAttributeDto> createAttribute(@Valid @RequestBody AttributeCreateRequest request) {
        ProductAttributeDto attribute = attributeService.createAttribute(request);
        return ApiResponse.success(attribute, "Attribute created successfully");
    }

    @GetMapping
    @Operation(summary = "List all attribute definitions")
    public ApiResponse<Page<ProductAttributeDto>> listAttributes(@PageableDefault(size = 20) Pageable pageable) {
        Page<ProductAttributeDto> attributes = attributeService.listAttributes(pageable);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(attributes.getNumber())
                .size(attributes.getSize())
                .totalElements(attributes.getTotalElements())
                .totalPages(attributes.getTotalPages())
                .hasNext(attributes.hasNext())
                .hasPrevious(attributes.hasPrevious())
                .build();
        return ApiResponse.success(attributes, pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get attribute by ID")
    public ApiResponse<ProductAttributeDto> getAttribute(@PathVariable Long id) {
        ProductAttributeDto attribute = attributeService.getAttributeById(id);
        return ApiResponse.success(attribute);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attribute definition")
    public void deleteAttribute(@PathVariable Long id) {
        attributeService.deleteAttribute(id);
    }

    @PostMapping("/products/{productId}/values")
    @Operation(summary = "Set attribute value for a product")
    public ApiResponse<AttributeValueDto> setAttributeValue(
            @PathVariable Long productId,
            @Valid @RequestBody AttributeValueSetRequest request) {
        AttributeValueDto value = attributeService.setAttributeValue(productId, request);
        return ApiResponse.success(value, "Attribute value set successfully");
    }

    @GetMapping("/products/{productId}/values")
    @Operation(summary = "Get all attribute values for a product")
    public ApiResponse<List<AttributeValueDto>> getAttributeValues(@PathVariable Long productId) {
        List<AttributeValueDto> values = attributeService.getAttributeValues(productId);
        return ApiResponse.success(values);
    }
}
