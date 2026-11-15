package com.opencrm.product.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencrm.product.config.GlobalExceptionHandler;
import com.opencrm.common.event.OutboxEventPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * DRG-754 / H-CPQ-P-004: the catalog search box combined with a Category or Status filter only
 * refined page 1 in the browser, because /products/search took no filters. The filters must
 * reach the repository query so they apply to the whole result set.
 */
class ProductSearchFiltersTest {

    private MockMvc mvc(ProductService service) {
        return MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void searchEndpointPassesCategoryAndStatusToTheService() throws Exception {
        var service = mock(ProductService.class);
        when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc(service).perform(get("/api/v1/products/search")
                        .param("q", "H4").param("category", "SUPPORT").param("status", "DRAFT"))
                .andExpect(status().isOk());

        verify(service).search(eq("H4"), eq(ProductCategory.SUPPORT), eq(ProductStatus.DRAFT), any(Pageable.class));
    }

    @Test
    void searchEndpointWithoutFiltersStillSearchesEverything() throws Exception {
        var service = mock(ProductService.class);
        when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc(service).perform(get("/api/v1/products/search").param("q", "H4")).andExpect(status().isOk());

        verify(service).search(eq("H4"), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void serviceSearchFiltersInTheRepositoryQuery() {
        var repository = mock(ProductRepository.class);
        var service = new ProductService(repository, mock(ProductMapper.class), mock(OutboxEventPublisher.class));
        Pageable page = PageRequest.of(0, 20);
        when(repository.search("H4", ProductCategory.SUPPORT, null, page)).thenReturn(new PageImpl<>(List.of()));

        service.search("H4", ProductCategory.SUPPORT, null, page);

        verify(repository).search("H4", ProductCategory.SUPPORT, null, page);
    }
}
