package com.opencrm.product.catalog;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.product.bundle.*;
import com.opencrm.product.config.GlobalExceptionHandler;
import com.opencrm.product.serving.CatalogCacheAdvice;
import com.opencrm.product.serving.CatalogReadController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MVC caching negotiation, with DTO services isolated from the database. No latency claim. */
class CatalogServingHttpTest {
    ProductService products;
    ProductBundleService bundles;
    ProductDto product;
    ProductBundleDto bundle;
    MockMvc mvc;

    @BeforeEach void setup() {
        products = mock(ProductService.class); bundles = mock(ProductBundleService.class);
        product = ProductDto.builder().id(1L).sku("SKU-1").name("Sellable product")
                .status(ProductStatus.ACTIVE).version(2L).build();
        bundle = ProductBundleDto.builder().id(3L).productId(1L).name("Bundle").version(5L)
                .items(List.of(BundleItemDto.builder().id(9L).productId(2L).quantity(2).build())).build();
        when(products.getById(1L)).thenReturn(product);
        when(products.getBySku("SKU-1")).thenReturn(product);
        when(products.list(any(), isNull(), isNull(), eq(ProductStatus.ACTIVE)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));
        when(bundles.getById(3L)).thenReturn(bundle);
        var converter = new JacksonJsonHttpMessageConverter();
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(products), new ProductBundleController(bundles),
                        new CatalogReadController(products, bundles))
                .setControllerAdvice(new GlobalExceptionHandler(), new CatalogCacheAdvice(converter.getMapper()))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter).build();
    }

    String etag(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andReturn().getResponse().getHeader("ETag");
    }

    @Test void existingProductGetRemainsDtoAndHasPrivateSemanticVersion() throws Exception {
        var result = mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-1"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andReturn().getResponse();
        assertThat(result.getHeader("ETag")).startsWith("W/\"").endsWith("\"");
        assertThat(result.getHeader("X-Catalog-Version")).matches("[0-9a-f]{64}");
        assertThat(result.getHeader("Vary")).contains("Authorization");
    }

    @Test void unchangedPayloadIgnoresEnvelopeTimestampAndHonorsWeakStrongMultipleAndWildcardTags() throws Exception {
        String tag = etag("/api/v1/products/1");
        assertThat(etag("/api/v1/products/1")).isEqualTo(tag);
        for (String candidate : List.of(tag, tag.substring(2), "\"other\", " + tag, "*")) {
            mvc.perform(get("/api/v1/products/1").header("If-None-Match", candidate))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).as(candidate).isEqualTo(304))
                    .andExpect(content().string(""))
                    .andExpect(header().string("ETag", tag));
        }
        mvc.perform(get("/api/v1/products/1").header("If-None-Match", "\"stale\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Sellable product"));
    }

    @Test void productMutationChangesTagAndOldConditionalRequestReturnsFreshDto() throws Exception {
        String old = etag("/api/v1/products/1");
        product.setName("Revised"); product.setVersion(3L);
        var response = mvc.perform(get("/api/v1/products/1").header("If-None-Match", old))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Revised"))
                .andReturn().getResponse();
        assertThat(response.getHeader("ETag")).isNotEqualTo(old);
    }

    @Test void skuAndHeadUseSameConditionalContract() throws Exception {
        String tag = etag("/api/v1/products/sku/SKU-1");
        mvc.perform(head("/api/v1/products/sku/SKU-1").header("If-None-Match", tag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
    }

    @Test void sellableListForcesActiveAndPreservesExistingPageDtoShape() throws Exception {
        String path = "/api/v1/products/sellable";
        mvc.perform(get(path).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
        verify(products).list(argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5),
                isNull(), isNull(), eq(ProductStatus.ACTIVE));
        String tag = etag(path);
        mvc.perform(get(path).header("If-None-Match", tag)).andExpect(status().isNotModified());
    }

    @Test void resolverReusesBundleCompositionAndItemChangesInvalidateEvenWithoutParentVersionChange() throws Exception {
        String path = "/api/v1/product-bundles/3/resolve";
        String old = etag(path);
        mvc.perform(get(path)).andExpect(jsonPath("$.data.items[0].productId").value(2))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
        bundle.getItems().getFirst().setQuantity(4);
        var response = mvc.perform(get(path).header("If-None-Match", old)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(4)).andReturn().getResponse();
        assertThat(response.getHeader("ETag")).isNotEqualTo(old);
        assertThat(etag("/api/v1/product-bundles/3")).isEqualTo(response.getHeader("ETag"));
    }

    @Test void changedPaginationTotalsInvalidateEvenWhenVisibleProductsAreUnchanged() throws Exception {
        String path = "/api/v1/products/sellable";
        String old = etag(path);
        when(products.list(any(), isNull(), isNull(), eq(ProductStatus.ACTIVE)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 1), 2));
        mvc.perform(get(path).header("If-None-Match", old)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(2));
        assertThat(etag(path)).isNotEqualTo(old);
    }

    @Test void validatorsNeverMaskMissingResourcesOrApplyToWrites() throws Exception {
        when(products.getById(99L)).thenThrow(new ResourceNotFoundException("Product", 99L));
        mvc.perform(get("/api/v1/products/99").header("If-None-Match", "*"))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist("ETag"));
        when(products.create(any())).thenReturn(product);
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"SKU-1","name":"Product","productType":"HARDWARE","category":"ADD_ON"}
                """))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("ETag"));
    }
}
