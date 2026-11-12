package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.CatalogPublishedEvent;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * PublishCatalog (DRG-421 / BC-01-04): freeze seller-visible products into an immutable catalog version and emit
 * CatalogPublished. Admin live product APIs remain mutable; seller reads go through published snapshots only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogPublishService {

    static final List<ProductStatus> PUBLISHABLE_STATUSES = List.of(ProductStatus.ACTIVE, ProductStatus.DEPRECATED);

    private final CatalogVersionRepository catalogVersionRepository;
    private final CatalogVersionProductRepository catalogVersionProductRepository;
    private final ProductRepository productRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * Creates the next immutable catalog version for the caller's tenant from current ACTIVE/DEPRECATED products.
     * Same-tenant publishes take a Postgres transaction advisory lock before {@code max(version_number)+1}.
     */
    public CatalogVersionDto publish() {
        String tenantId = ProductTenantResolver.currentTenantId();
        // Serialize max(version_number)+1 for this tenant bucket (including unresolved/null).
        catalogVersionRepository.lockTenantForPublish(tenantId == null ? "" : tenantId);
        Instant publishedAt = Instant.now();
        int nextNumber = catalogVersionRepository.findMaxVersionNumberByTenantId(tenantId) + 1;

        List<Product> publishable = productRepository.findPublishableByTenantIdAndStatusIn(
                tenantId, PUBLISHABLE_STATUSES);

        CatalogVersion version = CatalogVersion.builder()
                .versionNumber(nextNumber)
                .tenantId(tenantId)
                .publishedAt(publishedAt)
                .publishedBy(currentActorId())
                .productCount(publishable.size())
                .build();
        CatalogVersion saved = catalogVersionRepository.saveAndFlush(version);

        List<CatalogVersionProduct> entries = publishable.stream()
                .map(product -> toSnapshotEntry(saved.getId(), product))
                .toList();
        catalogVersionProductRepository.saveAll(entries);

        outboxEventPublisher.publish(
                "CatalogVersion",
                saved.getUuid(),
                EventContractRegistry.CATALOG_PUBLISHED_V1,
                new CatalogPublishedEvent(
                        saved.getUuid(),
                        saved.getVersionNumber(),
                        saved.getTenantId(),
                        saved.getProductCount(),
                        saved.getPublishedAt()
                )
        );

        log.info("Published catalog version {} (#{}) with {} products for tenant {}",
                saved.getUuid(), saved.getVersionNumber(), saved.getProductCount(), tenantId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public CatalogVersionDto getByVersionId(String versionId) {
        return toDto(requireVersion(versionId));
    }

    @Transactional(readOnly = true)
    public CatalogVersionDto getLatest() {
        String tenantId = ProductTenantResolver.currentTenantId();
        CatalogVersion latest = catalogVersionRepository.findLatestByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogVersion for tenant: "
                        + (tenantId == null ? "<unresolved>" : tenantId)));
        return toDto(latest);
    }

    /**
     * Pin resolution hook for Configuration StartSession ({@code catalogVersion}) and Pricing consumers.
     */
    @Transactional(readOnly = true)
    public CatalogPinDto resolvePin(String versionId) {
        String tenantId = ProductTenantResolver.currentTenantId();
        return catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse(versionId, tenantId)
                .map(version -> CatalogPinDto.builder()
                        .versionId(version.getUuid())
                        .versionNumber(version.getVersionNumber())
                        .publishedAt(version.getPublishedAt())
                        .productCount(version.getProductCount())
                        .resolvable(true)
                        .build())
                .orElseGet(() -> CatalogPinDto.builder()
                        .versionId(versionId)
                        .resolvable(false)
                        .productCount(0)
                        .build());
    }

    /**
     * Seller-facing product list for a published version. Draft and post-publish live edits are invisible here.
     */
    @Transactional(readOnly = true)
    public List<PublishedProductDto> listPublishedProducts(String versionId) {
        CatalogVersion version = requireVersion(versionId);
        return catalogVersionProductRepository.findByCatalogVersionIdOrderBySkuAsc(version.getId()).stream()
                .map(entry -> toPublishedDto(version.getUuid(), entry))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublishedProductDto> listLatestPublishedProducts() {
        return listPublishedProducts(getLatest().getVersionId());
    }

    @Transactional(readOnly = true)
    public PublishedProductDto getPublishedProduct(String versionId, Long productId) {
        CatalogVersion version = requireVersion(versionId);
        CatalogVersionProduct entry = catalogVersionProductRepository
                .findByCatalogVersionIdAndProductId(version.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product " + productId + " in catalog version " + versionId));
        return toPublishedDto(version.getUuid(), entry);
    }

    private CatalogVersion requireVersion(String versionId) {
        String tenantId = ProductTenantResolver.currentTenantId();
        return catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse(versionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CatalogVersion", versionId));
    }

    private static CatalogVersionProduct toSnapshotEntry(Long catalogVersionId, Product product) {
        return CatalogVersionProduct.builder()
                .catalogVersionId(catalogVersionId)
                .productId(product.getId())
                .productUuid(product.getUuid())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .productType(product.getProductType())
                .category(product.getCategory())
                .status(product.getStatus())
                .listPrice(product.getListPrice())
                .currency(product.getCurrency())
                .billingFrequency(product.getBillingFrequency())
                .unitOfMeasure(product.getUnitOfMeasure())
                .effectiveFrom(product.getEffectiveFrom())
                .effectiveTo(product.getEffectiveTo())
                .configurable(product.isConfigurable())
                .bundle(product.isBundle())
                .build();
    }

    private static CatalogVersionDto toDto(CatalogVersion version) {
        return CatalogVersionDto.builder()
                .versionId(version.getUuid())
                .versionNumber(version.getVersionNumber())
                .tenantId(version.getTenantId())
                .publishedAt(version.getPublishedAt())
                .publishedBy(version.getPublishedBy())
                .productCount(version.getProductCount())
                .build();
    }

    private static PublishedProductDto toPublishedDto(String versionId, CatalogVersionProduct entry) {
        return PublishedProductDto.builder()
                .catalogVersionId(versionId)
                .productId(entry.getProductId())
                .productUuid(entry.getProductUuid())
                .sku(entry.getSku())
                .name(entry.getName())
                .description(entry.getDescription())
                .productType(entry.getProductType())
                .category(entry.getCategory())
                .status(entry.getStatus())
                .listPrice(entry.getListPrice())
                .currency(entry.getCurrency())
                .billingFrequency(entry.getBillingFrequency())
                .unitOfMeasure(entry.getUnitOfMeasure())
                .effectiveFrom(entry.getEffectiveFrom())
                .effectiveTo(entry.getEffectiveTo())
                .configurable(entry.isConfigurable())
                .bundle(entry.isBundle())
                .build();
    }

    private static String currentActorId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getUserId)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
    }
}
