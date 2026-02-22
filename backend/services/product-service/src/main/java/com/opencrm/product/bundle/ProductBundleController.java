package com.opencrm.product.bundle;

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
@RequestMapping("/api/v1/product-bundles")
@RequiredArgsConstructor
@Tag(name = "Product Bundles", description = "Bundle management endpoints")
public class ProductBundleController {

    private final ProductBundleService bundleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product bundle")
    public ApiResponse<ProductBundleDto> create(@Valid @RequestBody ProductBundleCreateRequest request) {
        ProductBundleDto bundle = bundleService.create(request);
        return ApiResponse.success(bundle, "Bundle created successfully");
    }

    @GetMapping
    @Operation(summary = "List all bundles")
    public ApiResponse<Page<ProductBundleDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<ProductBundleDto> bundles = bundleService.list(pageable);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(bundles.getNumber())
                .size(bundles.getSize())
                .totalElements(bundles.getTotalElements())
                .totalPages(bundles.getTotalPages())
                .hasNext(bundles.hasNext())
                .hasPrevious(bundles.hasPrevious())
                .build();
        return ApiResponse.success(bundles, pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bundle by ID")
    public ApiResponse<ProductBundleDto> getById(@PathVariable Long id) {
        ProductBundleDto bundle = bundleService.getById(id);
        return ApiResponse.success(bundle);
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get bundles by product ID")
    public ApiResponse<List<ProductBundleDto>> getByProductId(@PathVariable Long productId) {
        List<ProductBundleDto> bundles = bundleService.getByProductId(productId);
        return ApiResponse.success(bundles);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Add item to bundle")
    public ApiResponse<ProductBundleDto> addItem(
            @PathVariable Long id,
            @Valid @RequestBody BundleItemCreateRequest request) {
        ProductBundleDto bundle = bundleService.addItem(id, request);
        return ApiResponse.success(bundle, "Item added to bundle");
    }

    @DeleteMapping("/{bundleId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove item from bundle")
    public void removeItem(@PathVariable Long bundleId, @PathVariable Long itemId) {
        bundleService.removeItem(bundleId, itemId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a bundle")
    public void delete(@PathVariable Long id) {
        bundleService.delete(id);
    }
}
