package com.opencrm.product.serving;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.product.bundle.ProductBundleDto;
import com.opencrm.product.bundle.ProductBundleService;
import com.opencrm.product.catalog.ProductDto;
import com.opencrm.product.catalog.ProductService;
import com.opencrm.product.catalog.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/** Read adapters over the existing catalog DTO contracts; no catalog write or snapshot policy. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Catalog serving", description = "Typed current catalog queries with representation validators")
public class CatalogReadController {
    private final ProductService products;
    private final ProductBundleService bundles;

    @GetMapping("/api/v1/products/sellable")
    @Operation(summary = "List active, nondeleted products", description = "Uses existing ProductDto pagination; cache version is a content digest, not a published release")
    public ApiResponse<Page<ProductDto>> listSellable(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<ProductDto> page = products.list(pageable, null, null, ProductStatus.ACTIVE);
        return ApiResponse.success(page, ApiResponse.PageInfo.builder()
                .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()).hasNext(page.hasNext()).hasPrevious(page.hasPrevious()).build());
    }

    @GetMapping("/api/v1/product-bundles/{id}/resolve")
    @Operation(summary = "Resolve the current bundle composition", description = "Returns the existing ProductBundleDto with active items; nested expansion and released catalog snapshots are separate capabilities")
    public ApiResponse<ProductBundleDto> resolveBundle(@PathVariable Long id) {
        return ApiResponse.success(bundles.getById(id));
    }
}
