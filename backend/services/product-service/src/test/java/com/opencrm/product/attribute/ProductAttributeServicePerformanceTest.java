package com.opencrm.product.attribute;

import com.opencrm.product.catalog.Product;
import com.opencrm.product.catalog.ProductType;
import com.opencrm.product.catalog.ProductCategory;
import com.opencrm.product.catalog.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductAttributeServicePerformanceTest {

    @InjectMocks
    private ProductAttributeService service;

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @Mock
    private ProductRepository productRepository;

    @Test
    public void testGetAttributeValuesPerformance() {
        // Setup
        long productId = 1L;
        List<ProductAttributeValue> values = new ArrayList<>();
        List<ProductAttribute> attributes = new ArrayList<>();

        for (long i = 0; i < 500; i++) {
            ProductAttribute attr = new ProductAttribute();
            attr.setId(i);
            attr.setName("Attr " + i);
            attr.setDataType(AttributeDataType.STRING);
            attributes.add(attr);

            // Mock individual attribute lookup for baseline testing
            when(attributeRepository.findByIdAndDeletedFalse(i))
                    .thenReturn(Optional.of(attr));

            ProductAttributeValue val = new ProductAttributeValue();
            val.setId(i);
            val.setProductId(productId);
            val.setAttributeId(i);
            val.setValue("Val " + i);
            values.add(val);
        }

        when(attributeValueRepository.findByProductIdAndDeletedFalse(productId))
                .thenReturn(values);

        // Mock batch attribute lookup for optimized testing
        when(attributeRepository.findByIdInAndDeletedFalse(any()))
                .thenReturn(attributes);

        // Warm up
        for (int i = 0; i < 5; i++) {
            service.getAttributeValues(productId);
        }

        // Measure
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            service.getAttributeValues(productId);
        }
        long end = System.currentTimeMillis();

        System.out.println("⚡ PERFORMANCE MEASUREMENT ⚡");
        System.out.println("Time taken for 100 iterations of 500 attributes: " + (end - start) + "ms");
    }
}
