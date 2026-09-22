package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.OutboxEventRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Product retirement with Kafka Transactional Outbox.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Product Retire - State Machine + Outbox")
class ProductRetireTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ProductService productService;

    private Product activeProduct;

    @BeforeEach
    void setUp() {
        activeProduct = Product.builder()
                .sku("SKU-001")
                .name("Test Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();
        activeProduct.setId(1L);
    }

    @Test
    @DisplayName("should retire an ACTIVE product and publish outbox event")
    void retireActiveProduct() {
        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);
        when(productMapper.toDto(any(Product.class))).thenReturn(new ProductDto());

        productService.retire(1L, "End of life");

        assertEquals(ProductStatus.RETIRED, activeProduct.getStatus());
        assertNotNull(activeProduct.getRetiredAt());
        assertEquals("End of life", activeProduct.getRetirementReason());

        verify(outboxEventPublisher).publish(
                eq("Product"),
                eq("1"),
                eq(EventContractRegistry.PRODUCT_RETIRED_V1),
                any()
        );
    }

    @Test
    @DisplayName("should reject retiring a DRAFT product")
    void rejectRetireDraft() {
        activeProduct.setStatus(ProductStatus.DRAFT);
        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(activeProduct));

        assertThrows(IllegalStateException.class, () -> productService.retire(1L, "reason"));
    }

    @Test
    @DisplayName("should throw when product not found")
    void productNotFound() {
        when(productRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.retire(99L, "reason"));
    }
}
