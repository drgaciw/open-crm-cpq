package com.opencrm.product.bundle;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.product.catalog.Product;
import com.opencrm.product.catalog.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Bundle Validation")
class ProductBundleServiceTest {

    @Mock
    private ProductBundleRepository bundleRepository;

    @Mock
    private BundleItemRepository bundleItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductBundleMapper bundleMapper;

    @InjectMocks
    private ProductBundleService bundleService;

    @Test
    @DisplayName("should reject bundle create when min items exceeds max items")
    void rejectInvalidMinMax() {
        ProductBundleCreateRequest request = ProductBundleCreateRequest.builder()
                .productId(1L)
                .name("Bundle")
                .minItems(3)
                .maxItems(2)
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> bundleService.create(request));

        assertEquals("BUNDLE_MIN_MAX_INVALID", exception.getErrorCode());
        verify(productRepository, never()).findByIdAndDeletedFalse(1L);
        verify(bundleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should create bundle when min and max constraints are valid")
    void createWithValidMinMax() {
        ProductBundleCreateRequest request = ProductBundleCreateRequest.builder()
                .productId(1L)
                .name("Bundle")
                .minItems(1)
                .maxItems(2)
                .build();
        ProductBundle bundle = ProductBundle.builder().productId(1L).name("Bundle").build();
        ProductBundle saved = ProductBundle.builder().productId(1L).name("Bundle").minItems(1).maxItems(2).build();
        ProductBundleDto dto = ProductBundleDto.builder().productId(1L).name("Bundle").minItems(1).maxItems(2).build();

        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(Product.builder().build()));
        when(bundleMapper.toEntity(request)).thenReturn(bundle);
        when(bundleRepository.save(bundle)).thenReturn(saved);
        when(bundleMapper.toDto(saved)).thenReturn(dto);

        ProductBundleDto result = bundleService.create(request);

        assertEquals(1, result.getMinItems());
        assertEquals(2, result.getMaxItems());
    }
}
