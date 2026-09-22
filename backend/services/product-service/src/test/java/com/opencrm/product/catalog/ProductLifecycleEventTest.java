package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
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
@DisplayName("Product Lifecycle Events")
class ProductLifecycleEventTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .sku("SKU-001")
                .name("Test Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.DRAFT)
                .listPrice(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();
        product.setId(1L);
    }

    @Test
    @DisplayName("should publish lifecycle event when product is activated")
    void activatePublishesLifecycleEvent() {
        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toDto(product)).thenReturn(new ProductDto());

        productService.activate(1L);

        verify(outboxEventPublisher).publish(
                eq("Product"),
                eq("1"),
                eq(EventContractRegistry.PRODUCT_UPDATED_V1),
                any()
        );
    }

    @Test
    @DisplayName("should publish lifecycle event when product metadata is updated")
    void updatePublishesLifecycleEvent() {
        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toDto(product)).thenReturn(new ProductDto());

        productService.update(1L, new ProductUpdateRequest());

        verify(outboxEventPublisher).publish(
                eq("Product"),
                eq("1"),
                eq(EventContractRegistry.PRODUCT_UPDATED_V1),
                any()
        );
    }
}
