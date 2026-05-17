package com.opencrm.product.option;

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
@DisplayName("Product Option Group Constraints")
class ProductOptionGroupConstraintTest {

    @Mock
    private ProductOptionGroupRepository optionGroupRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionMapper optionMapper;

    @Spy
    private OptionGroupConstraintValidator optionGroupConstraintValidator = new OptionGroupConstraintValidator();

    @InjectMocks
    private ProductOptionService optionService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .sku("SKU-OPT")
                .name("Option Product")
                .productType(ProductType.CUSTOM)
                .category(ProductCategory.SUBSCRIPTION)
                .status(ProductStatus.ACTIVE)
                .listPrice(BigDecimal.TEN)
                .currency("USD")
                .build();
        product.setId(1L);
    }

    @Test
    @DisplayName("should reject option group when max is less than min")
    void rejectInvalidMinMaxDefinition() {
        OptionGroupCreateRequest request = OptionGroupCreateRequest.builder()
                .productId(1L)
                .name("RAM")
                .selectionType(SelectionType.SINGLE)
                .minSelections(2)
                .maxSelections(1)
                .build();

        when(productRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));

        assertThrows(BusinessException.class, () -> optionService.createGroup(request));
        verify(optionGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("should reject adding options beyond configured max")
    void rejectTooManyOptions() {
        ProductOptionGroup group = ProductOptionGroup.builder()
                .productId(1L)
                .name("Addons")
                .selectionType(SelectionType.MULTIPLE)
                .minSelections(0)
                .maxSelections(1)
                .build();
        group.setId(10L);
        group.getOptions().add(ProductOption.builder().name("A").productId(1L).build());

        OptionCreateRequest request = OptionCreateRequest.builder()
                .productId(1L)
                .name("B")
                .build();

        when(optionGroupRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(group));

        assertThrows(BusinessException.class, () -> optionService.addOption(10L, request));
        verify(optionGroupRepository, never()).save(any());
    }
}
