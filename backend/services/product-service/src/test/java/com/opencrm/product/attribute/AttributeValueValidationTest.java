package com.opencrm.product.attribute;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Attribute Value Validation")
class AttributeValueValidationTest {

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductAttributeMapper attributeMapper;

    @Spy
    private AttributeValueValidator attributeValueValidator = new AttributeValueValidator();

    @InjectMocks
    private ProductAttributeService attributeService;

    private Product product;
    private ProductAttribute enumAttribute;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .sku("SKU-ATTR")
                .name("Attr Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(BigDecimal.TEN)
                .currency("USD")
                .build();
        product.setId(1L);

        enumAttribute = ProductAttribute.builder()
                .name("tier")
                .dataType(AttributeDataType.ENUM)
                .enumValues("standard,premium")
                .build();
        enumAttribute.setId(5L);
    }

    @Test
    @DisplayName("should reject enum attribute values outside allowed set")
    void rejectInvalidEnumValue() {
        AttributeValueSetRequest request = AttributeValueSetRequest.builder()
                .attributeId(5L)
                .value("enterprise")
                .build();

        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));
        when(attributeRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(enumAttribute));

        assertThrows(BusinessException.class, () -> attributeService.setAttributeValue(1L, request));
        verify(attributeValueRepository, never()).save(any());
    }
}
