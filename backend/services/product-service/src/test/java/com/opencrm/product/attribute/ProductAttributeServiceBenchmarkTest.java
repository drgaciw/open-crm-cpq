package com.opencrm.product.attribute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductAttributeServiceBenchmarkTest {

    @Mock
    private ProductAttributeRepository attributeRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @InjectMocks
    private ProductAttributeService attributeService;

    @Test
    public void testGetAttributeValuesPerformance() throws Exception {
        int count = 500;
        List<ProductAttributeValue> values = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            ProductAttributeValue val = ProductAttributeValue.builder()
                    .productId(1L)
                    .attributeId(i)
                    .value("Val" + i)
                    .build();
            val.setId(i);
            values.add(val);
        }

        when(attributeValueRepository.findByProductIdAndDeletedFalse(1L)).thenReturn(values);

        when(attributeRepository.findByIdInAndDeletedFalse(org.mockito.ArgumentMatchers.anySet())).thenAnswer(inv -> {
            Thread.sleep(10); // Simulate 10ms DB latency for batch query
            Set<Long> ids = inv.getArgument(0);
            List<ProductAttribute> attrs = new ArrayList<>();
            for (Long id : ids) {
                ProductAttribute attr = ProductAttribute.builder().name("Attr" + id).build();
                attr.setId(id);
                attrs.add(attr);
            }
            return attrs;
        });

        long start = System.currentTimeMillis();
        List<AttributeValueDto> result = attributeService.getAttributeValues(1L);
        long end = System.currentTimeMillis();
        System.out.println("BASELINE_TIME: " + (end - start) + "ms");
    }
}
