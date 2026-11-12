package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.CatalogPublishedEvent;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-421 / BC-01-04: PublishCatalog, CatalogPublished payload, and seller snapshot invisibility of drafts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Catalog Publish (DRG-421)")
class CatalogPublishServiceTest {

    @Mock
    private CatalogVersionRepository catalogVersionRepository;

    @Mock
    private CatalogVersionProductRepository catalogVersionProductRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private CatalogPublishService catalogPublishService;

    private Product activeProduct;
    private Product draftProduct;

    @BeforeEach
    void setUp() {
        activeProduct = Product.builder()
                .sku("SKU-ACTIVE")
                .name("Active Widget")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(new BigDecimal("42.00"))
                .currency("USD")
                .unitOfMeasure("EACH")
                .build();
        activeProduct.setId(10L);
        activeProduct.setUuid(UUID.randomUUID().toString());

        draftProduct = Product.builder()
                .sku("SKU-DRAFT")
                .name("Draft Widget")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.DRAFT)
                .build();
        draftProduct.setId(11L);
        draftProduct.setUuid(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("publish() freezes ACTIVE products, skips drafts, and emits CatalogPublished with version id")
    void publishEmitsCatalogPublishedWithVersionId() {
        when(catalogVersionRepository.lockTenantForPublish("")).thenReturn(0);
        when(catalogVersionRepository.findMaxVersionNumberByTenantId(isNull())).thenReturn(0);
        when(productRepository.findPublishableByTenantIdAndStatusIn(
                isNull(), eq(CatalogPublishService.PUBLISHABLE_STATUSES)))
                .thenReturn(List.of(activeProduct));
        when(catalogVersionRepository.saveAndFlush(any(CatalogVersion.class))).thenAnswer(invocation -> {
            CatalogVersion toSave = invocation.getArgument(0);
            toSave.setId(100L);
            if (toSave.getUuid() == null) {
                toSave.setUuid("catalog-version-uuid-1");
            }
            return toSave;
        });
        when(catalogVersionProductRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogVersionDto result = catalogPublishService.publish();

        assertThat(result.getVersionId()).isEqualTo("catalog-version-uuid-1");
        assertThat(result.getVersionNumber()).isEqualTo(1);
        assertThat(result.getProductCount()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CatalogVersionProduct>> entries = ArgumentCaptor.forClass(List.class);
        verify(catalogVersionProductRepository).saveAll(entries.capture());
        assertThat(entries.getValue()).hasSize(1);
        assertThat(entries.getValue().getFirst().getSku()).isEqualTo("SKU-ACTIVE");
        assertThat(entries.getValue().getFirst().getProductId()).isEqualTo(10L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("CatalogVersion"),
                eq("catalog-version-uuid-1"),
                eq(EventContractRegistry.CATALOG_PUBLISHED_V1),
                payload.capture());
        CatalogPublishedEvent event = (CatalogPublishedEvent) payload.getValue();
        assertThat(event.getVersionId()).isEqualTo("catalog-version-uuid-1");
        assertThat(event.getVersionNumber()).isEqualTo(1);
        assertThat(event.getProductCount()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("seller listPublishedProducts hides drafts and post-publish live edits (snapshot only)")
    void sellerReadsUseImmutableSnapshotNotLiveDrafts() {
        CatalogVersion version = CatalogVersion.builder()
                .versionNumber(1)
                .productCount(1)
                .build();
        version.setId(100L);
        version.setUuid("catalog-version-uuid-1");

        CatalogVersionProduct frozen = CatalogVersionProduct.builder()
                .catalogVersionId(100L)
                .productId(10L)
                .productUuid(activeProduct.getUuid())
                .sku("SKU-ACTIVE")
                .name("Active Widget")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(new BigDecimal("42.00"))
                .currency("USD")
                .build();

        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("catalog-version-uuid-1", null))
                .thenReturn(Optional.of(version));
        when(catalogVersionProductRepository.findByCatalogVersionIdOrderBySkuAsc(100L))
                .thenReturn(List.of(frozen));

        // Live catalog still has a draft and a revised name — seller path must not see them.
        assertThat(draftProduct.getStatus()).isEqualTo(ProductStatus.DRAFT);
        activeProduct.setName("Renamed After Publish");

        List<PublishedProductDto> sellerView =
                catalogPublishService.listPublishedProducts("catalog-version-uuid-1");

        assertThat(sellerView).hasSize(1);
        assertThat(sellerView.getFirst().getSku()).isEqualTo("SKU-ACTIVE");
        assertThat(sellerView.getFirst().getName()).isEqualTo("Active Widget");
        assertThat(sellerView).noneMatch(p -> "SKU-DRAFT".equals(p.getSku()));
        verify(productRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    @DisplayName("resolvePin returns resolvable=true with version metadata for Configuration/Pricing pins")
    void resolvePinReturnsCatalogVersionMetadata() {
        CatalogVersion version = CatalogVersion.builder()
                .versionNumber(3)
                .productCount(7)
                .build();
        version.setUuid("pin-version-id");
        version.setPublishedAt(java.time.Instant.parse("2026-11-12T00:00:00Z"));

        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("pin-version-id", null))
                .thenReturn(Optional.of(version));

        CatalogPinDto pin = catalogPublishService.resolvePin("pin-version-id");

        assertThat(pin.isResolvable()).isTrue();
        assertThat(pin.getVersionId()).isEqualTo("pin-version-id");
        assertThat(pin.getVersionNumber()).isEqualTo(3);
        assertThat(pin.getProductCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("resolvePin returns resolvable=false for an unknown version id")
    void resolvePinUnknownVersion() {
        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("missing", null))
                .thenReturn(Optional.empty());

        CatalogPinDto pin = catalogPublishService.resolvePin("missing");

        assertThat(pin.isResolvable()).isFalse();
        assertThat(pin.getVersionId()).isEqualTo("missing");
        assertThat(pin.getProductCount()).isZero();
    }

    @Test
    @DisplayName("getByVersionId rejects a version UUID that is not in the caller's tenant bucket")
    void getByVersionIdRejectsCrossTenantUuid() {
        // Unresolved caller (null tid) must not resolve a version owned by tenant-b via UUID alone.
        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("foreign-version", null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogPublishService.getByVersionId("foreign-version"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogVersionRepository).findByUuidAndTenantIdAndDeletedFalse("foreign-version", null);
    }

    @Test
    @DisplayName("resolvePin is not resolvable for a cross-tenant version UUID")
    void resolvePinRejectsCrossTenantUuid() {
        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("foreign-version", null))
                .thenReturn(Optional.empty());

        CatalogPinDto pin = catalogPublishService.resolvePin("foreign-version");

        assertThat(pin.isResolvable()).isFalse();
        assertThat(pin.getVersionId()).isEqualTo("foreign-version");
    }

    @Test
    @DisplayName("getPublishedProduct rejects a product id that was never frozen in the version")
    void getPublishedProductRejectsMissingSnapshotEntry() {
        CatalogVersion version = CatalogVersion.builder().versionNumber(1).productCount(0).build();
        version.setId(100L);
        version.setUuid("catalog-version-uuid-1");
        when(catalogVersionRepository.findByUuidAndTenantIdAndDeletedFalse("catalog-version-uuid-1", null))
                .thenReturn(Optional.of(version));
        when(catalogVersionProductRepository.findByCatalogVersionIdAndProductId(100L, 11L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogPublishService.getPublishedProduct("catalog-version-uuid-1", 11L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
