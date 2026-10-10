package com.opencrm.product.catalog;

import com.opencrm.product.config.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The sold hybrid classifications must be authorable through the same catalog HTTP boundary. */
class HybridProductCatalogHttpTest {
    @ParameterizedTest
    @ValueSource(strings = {"ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP", "SUPPORT_BASIC"})
    void catalogAcceptsAndReturnsFixedMigrationClassifications(String productType) throws Exception {
        var service = mock(ProductService.class);
        when(service.create(any())).thenAnswer(invocation -> {
            ProductCreateRequest request = invocation.getArgument(0);
            assertThat(request.getProductType().name()).isEqualTo(productType);
            assertThat(request.getBillingFrequency()).isEqualTo(BillingFrequency.MONTHLY);
            return ProductDto.builder().id(1L).sku(request.getSku()).name(request.getName())
                    .productType(request.getProductType()).category(request.getCategory())
                    .billingFrequency(request.getBillingFrequency()).build();
        });
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"SYNTHETIC-HYBRID","name":"Synthetic hybrid","productType":"%s",
                 "category":"SUPPORT","billingFrequency":"MONTHLY","listPrice":100,"currency":"USD"}
                """.formatted(productType)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productType").value(productType))
                .andExpect(jsonPath("$.data.billingFrequency").value("MONTHLY"));
        verify(service).create(any(ProductCreateRequest.class));
    }
}
