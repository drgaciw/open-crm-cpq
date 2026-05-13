package com.opencrm.product.attribute;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Attribute Validation")
class ProductAttributeServiceTest {

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductAttributeMapper attributeMapper;

    @InjectMocks
    private ProductAttributeService attributeService;

    @Test
    @DisplayName("should reject invalid default value at attribute create time")
    void rejectInvalidDefaultValue() {
        AttributeCreateRequest request = AttributeCreateRequest.builder()
                .name("seat_count")
                .dataType(AttributeDataType.NUMBER)
                .defaultValue("many")
                .build();

        when(attributeRepository.existsByNameAndDeletedFalse("seat_count")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> attributeService.createAttribute(request));

        assertEquals("ATTRIBUTE_VALUE_TYPE_INVALID", exception.getErrorCode());
        verify(attributeRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject enum value outside allowed values at write time")
    void rejectInvalidEnumValue() {
        AttributeValueSetRequest request = AttributeValueSetRequest.builder()
                .attributeId(10L)
                .value("XL")
                .build();
        ProductAttribute attribute = ProductAttribute.builder()
                .name("size")
                .dataType(AttributeDataType.ENUM)
                .enumValues("S,M,L")
                .build();

        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(Product.builder().build()));
        when(attributeRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(attribute));

        BusinessException exception = assertThrows(BusinessException.class, () -> attributeService.setAttributeValue(1L, request));

        assertEquals("ATTRIBUTE_VALUE_NOT_ALLOWED", exception.getErrorCode());
        verify(attributeValueRepository, never()).save(any());
    }

    @Test
    @DisplayName("should save valid attribute value")
    void saveValidAttributeValue() {
        AttributeValueSetRequest request = AttributeValueSetRequest.builder()
                .attributeId(10L)
                .value("M")
                .build();
        ProductAttribute attribute = ProductAttribute.builder()
                .name("size")
                .dataType(AttributeDataType.ENUM)
                .enumValues("S,M,L")
                .build();
        ProductAttributeValue saved = ProductAttributeValue.builder()
                .productId(1L)
                .attributeId(10L)
                .value("M")
                .build();
        saved.setId(22L);

        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(Product.builder().build()));
        when(attributeRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(attribute));
        when(attributeValueRepository.findByProductIdAndAttributeIdAndDeletedFalse(1L, 10L)).thenReturn(Optional.empty());
        when(attributeValueRepository.save(any(ProductAttributeValue.class))).thenReturn(saved);

        AttributeValueDto result = attributeService.setAttributeValue(1L, request);

        assertEquals(22L, result.getId());
        assertEquals("M", result.getValue());
        assertEquals("size", result.getAttributeName());
    }
}
