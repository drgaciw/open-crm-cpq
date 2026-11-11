package com.opencrm.product.catalog;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ProductDefinedEvent;
import com.opencrm.common.event.dto.ProductRevisedEvent;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-418 / BC-01-01: DefineProduct, ReviseProduct, and LifecycleState transition enforcement.
 *
 * <p>No security context is set up here (mirrors every other ProductService unit test), so
 * {@link ProductTenantResolver#currentTenantId()} resolves to null throughout — the same "unresolved tenant"
 * bucket that service tokens and dev mode fall into in production.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Product Define/Revise (DRG-418)")
class ProductDefineReviseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        lenient().when(productMapper.toEntity(any(ProductCreateRequest.class)))
                .thenAnswer(invocation -> {
                    ProductCreateRequest request = invocation.getArgument(0);
                    return Product.builder()
                            .sku(request.getSku())
                            .name(request.getName())
                            .productType(request.getProductType())
                            .category(request.getCategory())
                            .status(ProductStatus.DRAFT)
                            .unitOfMeasure(request.getUnitOfMeasure())
                            .effectiveFrom(request.getEffectiveFrom())
                            .effectiveTo(request.getEffectiveTo())
                            .build();
                });
        lenient().when(productMapper.toDto(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product product = invocation.getArgument(0);
                    return ProductDto.builder().id(product.getId()).sku(product.getSku())
                            .status(product.getStatus()).build();
                });
        lenient().doAnswer(invocation -> {
            ProductUpdateRequest request = invocation.getArgument(0);
            Product target = invocation.getArgument(1);
            if (request.getUnitOfMeasure() != null) {
                target.setUnitOfMeasure(request.getUnitOfMeasure());
            }
            if (request.getEffectiveFrom() != null) {
                target.setEffectiveFrom(request.getEffectiveFrom());
            }
            if (request.getStatus() != null) {
                target.setStatus(request.getStatus());
            }
            return null;
        }).when(productMapper).updateEntity(any(ProductUpdateRequest.class), any(Product.class));
    }

    @Test
    @DisplayName("create() publishes ProductDefined with the Sku, UnitOfMeasure, LifecycleState and EffectiveDateRange")
    void createPublishesProductDefined() {
        when(productRepository.existsByTenantIdAndSkuAndDeletedFalse(isNull(), anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product toSave = invocation.getArgument(0);
            toSave.setId(42L);
            return toSave;
        });

        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku("SKU-DEFINE-1")
                .name("Defined Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .unitOfMeasure("EACH")
                .effectiveFrom(LocalDate.of(2027, 1, 1))
                .effectiveTo(LocalDate.of(2027, 12, 31))
                .build();

        productService.create(request);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("Product"), eq("42"), eq(EventContractRegistry.PRODUCT_DEFINED_V1), payload.capture());
        ProductDefinedEvent event = (ProductDefinedEvent) payload.getValue();
        assertThat(event.getSku()).isEqualTo("SKU-DEFINE-1");
        assertThat(event.getUnitOfMeasure()).isEqualTo("EACH");
        assertThat(event.getLifecycleState()).isEqualTo("DRAFT");
        assertThat(event.getEffectiveFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(event.getEffectiveTo()).isEqualTo(LocalDate.of(2027, 12, 31));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isNull();
    }

    @Test
    @DisplayName("create() rejects a SKU already used within the same (unresolved) tenant")
    void createRejectsDuplicateSkuWithinTenant() {
        when(productRepository.existsByTenantIdAndSkuAndDeletedFalse(isNull(), eq("SKU-DUP"))).thenReturn(true);

        ProductCreateRequest request = ProductCreateRequest.builder()
                .sku("SKU-DUP")
                .name("Duplicate")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .build();

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("DUPLICATE_SKU");

        verify(productRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("update() publishes ProductRevised alongside the existing catalog-lifecycle event")
    void updatePublishesProductRevised() {
        Product product = Product.builder()
                .sku("SKU-REV-1")
                .name("Revisable")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.DRAFT)
                .listPrice(BigDecimal.TEN)
                .currency("USD")
                .build();
        product.setId(7L);

        when(productRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .unitOfMeasure("SEAT")
                .effectiveFrom(LocalDate.of(2027, 2, 1))
                .build();

        productService.update(7L, request);

        verify(outboxEventPublisher).publish(
                eq("Product"), eq("7"), eq(EventContractRegistry.PRODUCT_UPDATED_V1), any());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("Product"), eq("7"), eq(EventContractRegistry.PRODUCT_REVISED_V1), payload.capture());
        ProductRevisedEvent event = (ProductRevisedEvent) payload.getValue();
        assertThat(event.getUnitOfMeasure()).isEqualTo("SEAT");
        assertThat(event.getEffectiveFrom()).isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    @DisplayName("update() rejects a direct status jump that skips the state machine (Draft -> Retired)")
    void updateRejectsIllegalDirectStatusJump() {
        Product product = Product.builder()
                .sku("SKU-REV-2")
                .name("Guarded")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.DRAFT)
                .build();
        product.setId(8L);

        when(productRepository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(product));

        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .status(ProductStatus.RETIRED)
                .build();

        assertThatThrownBy(() -> productService.update(8L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("INVALID_STATUS_TRANSITION");

        verify(productRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("activate() rejects Deprecated -> Active, closing the bypass of the entity state machine")
    void activateRejectsReactivatingADeprecatedProduct() {
        Product product = Product.builder()
                .sku("SKU-REACT")
                .name("Deprecated")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.DEPRECATED)
                .build();
        product.setId(9L);

        when(productRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.activate(9L))
                .isInstanceOf(IllegalStateException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("getBySku() scopes the lookup to the caller's (here, unresolved) tenant")
    void getBySkuScopesLookupToTenant() {
        Product product = Product.builder().sku("SKU-LOOKUP").name("Found").build();
        product.setId(1L);
        when(productRepository.findByTenantIdAndSkuAndDeletedFalse(isNull(), eq("SKU-LOOKUP")))
                .thenReturn(Optional.of(product));

        ProductDto result = productService.getBySku("SKU-LOOKUP");

        assertThat(result.getSku()).isEqualTo("SKU-LOOKUP");
        verify(productRepository).findByTenantIdAndSkuAndDeletedFalse(isNull(), eq("SKU-LOOKUP"));
    }
}
