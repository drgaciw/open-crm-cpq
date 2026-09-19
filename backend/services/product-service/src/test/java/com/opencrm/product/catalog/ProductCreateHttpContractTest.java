package com.opencrm.product.catalog;

import com.opencrm.product.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductCreateHttpContractTest {
    private ProductService service;
    private MockMvc mvc;
    private static final String REQUIRED = "\"sku\":\"UAT-1\",\"name\":\"UAT Product\",\"productType\":\"HARDWARE\",\"category\":\"ADD_ON\"";

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void minimalSpaPayloadCreatesProductWithFalseOptionalFlags() throws Exception {
        when(service.create(any())).thenReturn(ProductDto.builder().id(1L).build());
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{" + REQUIRED + "}"))
                .andExpect(status().isCreated());
        var request = ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(service).create(request.capture());
        assertThat(request.getValue().isConfigurable()).isFalse();
        assertThat(request.getValue().isBundle()).isFalse();
    }

    @Test
    void explicitFlagsArePreserved() throws Exception {
        when(service.create(any())).thenReturn(ProductDto.builder().id(1L).build());
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{" + REQUIRED + ",\"configurable\":true,\"bundle\":false}"))
                .andExpect(status().isCreated());
        var request = ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(service).create(request.capture());
        assertThat(request.getValue().isConfigurable()).isTrue();
        assertThat(request.getValue().isBundle()).isFalse();
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void explicitNullBooleanRemainsInvalidWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                .content("{" + REQUIRED + ",\"configurable\":null,\"bundle\":false}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
