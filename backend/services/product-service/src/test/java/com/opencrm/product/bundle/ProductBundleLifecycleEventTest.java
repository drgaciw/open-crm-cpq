package com.opencrm.product.bundle;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.product.catalog.Product;
import com.opencrm.product.catalog.ProductCategory;
import com.opencrm.product.catalog.ProductRepository;
import com.opencrm.product.catalog.ProductStatus;
import com.opencrm.product.catalog.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Bundle Lifecycle Events")
class ProductBundleLifecycleEventTest {


    @Mock
    private ProductBundleRepository bundleRepository;

    @Mock
    private BundleItemRepository bundleItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductBundleMapper bundleMapper;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ProductBundleService bundleService;

    private ProductBundle bundle;
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .sku("SKU-001")
                .name("Test Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();
        product.setId(1L);

        bundle = ProductBundle.builder()
                .productId(1L)
                .name("Bundle")
                .status("ACTIVE")
                .build();
        bundle.setId(10L);
    }

    @Test
    @DisplayName("should publish bundle structure event when item is added")
    void addItemPublishesStructureEvent() {
        BundleItemCreateRequest request = new BundleItemCreateRequest();
        request.setProductId(1L);
        request.setQuantity(2);

        when(bundleRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(bundle));
        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));
        when(bundleRepository.save(bundle)).thenReturn(bundle);
        when(bundleMapper.toDto(bundle)).thenReturn(new ProductBundleDto());

        bundleService.addItem(10L, request);

        verify(outboxEventPublisher).publish(
                eq("ProductBundle"),
                eq("10"),
                eq(EventContractRegistry.BUNDLE_STRUCTURE_CHANGED_V1),
                any()
        );
    }

    @Test
    @DisplayName("should publish bundle structure event when item is removed")
    void removeItemPublishesStructureEvent() {
        BundleItem item = BundleItem.builder()
                .bundle(bundle)
                .productId(1L)
                .quantity(1)
                .build();
        item.setId(20L);
        bundle.getItems().add(item);

        when(bundleRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(bundle));
        when(bundleItemRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(item));

        bundleService.removeItem(10L, 20L);

        verify(outboxEventPublisher).publish(
                eq("ProductBundle"),
                eq("10"),
                eq(EventContractRegistry.BUNDLE_STRUCTURE_CHANGED_V1),
                any()
        );
    }
}
