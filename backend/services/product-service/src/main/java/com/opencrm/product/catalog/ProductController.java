package com.opencrm.product.catalog;

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

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management endpoints")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new product")
    public ApiResponse<ProductDto> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductDto product = productService.create(request);
        return ApiResponse.success(product, "Product created successfully");
    }

    @GetMapping
    @Operation(summary = "List products with optional filters")
    public ApiResponse<Page<ProductDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) ProductStatus status) {
        Page<ProductDto> products = productService.list(pageable, productType, category, status);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .hasNext(products.hasNext())
                .hasPrevious(products.hasPrevious())
                .build();
        return ApiResponse.success(products, pageInfo);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name, SKU, or description")
    public ApiResponse<Page<ProductDto>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductDto> products = productService.search(q, pageable);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .hasNext(products.hasNext())
                .hasPrevious(products.hasPrevious())
                .build();
        return ApiResponse.success(products, pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ApiResponse<ProductDto> getById(@PathVariable Long id) {
        ProductDto product = productService.getById(id);
        return ApiResponse.success(product);
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public ApiResponse<ProductDto> getBySku(@PathVariable String sku) {
        ProductDto product = productService.getBySku(sku);
        return ApiResponse.success(product);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ApiResponse<ProductDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductDto product = productService.update(id, request);
        return ApiResponse.success(product, "Product updated successfully");
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a product")
    public ApiResponse<ProductDto> activate(@PathVariable Long id) {
        ProductDto product = productService.activate(id);
        return ApiResponse.success(product, "Product activated successfully");
    }

    @PostMapping("/{id}/deprecate")
    @Operation(summary = "Deprecate a product")
    public ApiResponse<ProductDto> deprecate(@PathVariable Long id) {
        ProductDto product = productService.deprecate(id);
        return ApiResponse.success(product, "Product deprecated successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a product (soft delete)")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
