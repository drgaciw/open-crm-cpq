package com.opencrm.product.catalog;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PublishCatalog and seller-facing published-catalog reads (DRG-421 / BC-01-04).
 * Admin live product CRUD remains on {@link ProductController}.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog Versions", description = "Publish immutable catalog versions for seller/config/pricing pins")
public class CatalogController {

    private final CatalogPublishService catalogPublishService;

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "PublishCatalog — create an immutable catalog version and emit CatalogPublished")
    public ApiResponse<CatalogVersionDto> publish() {
        CatalogVersionDto version = catalogPublishService.publish();
        return ApiResponse.success(version, "Catalog version published successfully");
    }

    @GetMapping("/versions/latest")
    @Operation(summary = "Latest published catalog version for the caller's tenant")
    public ApiResponse<CatalogVersionDto> latest() {
        return ApiResponse.success(catalogPublishService.getLatest());
    }

    @GetMapping("/versions/{versionId}")
    @Operation(summary = "Get a published catalog version by id")
    public ApiResponse<CatalogVersionDto> getVersion(@PathVariable String versionId) {
        return ApiResponse.success(catalogPublishService.getByVersionId(versionId));
    }

    @GetMapping("/versions/{versionId}/pin")
    @Operation(summary = "Resolve a catalog version pin for Configuration or Pricing")
    public ApiResponse<CatalogPinDto> resolvePin(@PathVariable String versionId) {
        return ApiResponse.success(catalogPublishService.resolvePin(versionId));
    }

    @GetMapping("/versions/latest/products")
    @Operation(summary = "Seller-facing products from the latest published catalog version")
    public ApiResponse<List<PublishedProductDto>> latestProducts() {
        return ApiResponse.success(catalogPublishService.listLatestPublishedProducts());
    }

    @GetMapping("/versions/{versionId}/products")
    @Operation(summary = "Seller-facing products frozen in a catalog version (draft/live edits invisible)")
    public ApiResponse<List<PublishedProductDto>> products(@PathVariable String versionId) {
        return ApiResponse.success(catalogPublishService.listPublishedProducts(versionId));
    }

    @GetMapping("/versions/{versionId}/products/{productId}")
    @Operation(summary = "Seller-facing product from a pinned catalog version")
    public ApiResponse<PublishedProductDto> product(
            @PathVariable String versionId,
            @PathVariable Long productId) {
        return ApiResponse.success(catalogPublishService.getPublishedProduct(versionId, productId));
    }
}
