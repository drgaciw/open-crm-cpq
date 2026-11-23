package com.opencrm.product.bundle;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.product.catalog.ProductRepository;
import com.opencrm.product.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductBundleUpdateHttpTest {
    private final ProductBundleRepository bundles = mock(ProductBundleRepository.class);
    private final BundleItemRepository items = mock(BundleItemRepository.class);
    private final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
    private final ProductBundle bundle = ProductBundle.builder().id(7L).productId(45L).name("Original").build();
    private final BundleItem item = BundleItem.builder().id(8L).bundle(bundle).productId(46L).quantity(1).build();
    private final ProductBundleService service = new ProductBundleService(bundles, items,
            mock(ProductRepository.class), Mappers.getMapper(ProductBundleMapper.class), outbox);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProductBundleController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void updatesBundleMetadataWithoutReplacingItsProduct() throws Exception {
        when(bundles.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(bundle));
        when(bundles.save(bundle)).thenReturn(bundle);

        mvc.perform(put("/api/v1/product-bundles/7").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Updated","description":"New details","discountType":"NONE","minItems":0,"maxItems":2}
                """)).andExpect(status().isOk());

        assertThat(bundle.getName()).isEqualTo("Updated");
        assertThat(bundle.getDescription()).isEqualTo("New details");
        assertThat(bundle.getMaxItems()).isEqualTo(2);
        assertThat(bundle.getProductId()).isEqualTo(45L);
    }

    @Test
    void editsOwnedItemAndPublishesStructureChange() throws Exception {
        bundle.getItems().add(item);
        when(bundles.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(bundle));
        when(items.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(item));

        mvc.perform(put("/api/v1/product-bundles/7/items/8").contentType(MediaType.APPLICATION_JSON).content("""
                {"quantity":4,"optional":true,"sortOrder":3}
                """)).andExpect(status().isOk());

        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(item.isOptional()).isTrue();
        assertThat(item.getSortOrder()).isEqualTo(3);
        assertThat(item.getProductId()).isEqualTo(46L);
        verify(outbox).publish(eq("ProductBundle"), eq("7"), eq(EventContractRegistry.BUNDLE_STRUCTURE_CHANGED_V1), any());
    }

    @Test
    void rejectsAnItemOwnedByAnotherBundle() throws Exception {
        when(bundles.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(bundle));
        var other = ProductBundle.builder().id(6L).productId(99L).name("Other").build();
        item.setBundle(other);
        when(items.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(item));

        mvc.perform(put("/api/v1/product-bundles/7/items/8").contentType(MediaType.APPLICATION_JSON).content("""
                {"quantity":4,"optional":true,"sortOrder":3}
                """)).andExpect(status().isNotFound());
        assertThat(item.getQuantity()).isEqualTo(1);
    }
}
