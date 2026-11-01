package com.opencrm.product.option;

import com.opencrm.product.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA payloads for Add option omit {@code sortOrder}. Jackson must still bind
 * {@link OptionCreateRequest} (defaults to 0) so Add does not 400 INVALID_REQUEST_BODY.
 */
class OptionCreateHttpContractTest {

    private ProductOptionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProductOptionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductOptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("SPA payload without sortOrder adds option with sortOrder default 0")
    void spaPayloadWithoutSortOrderAddsOption() throws Exception {
        when(service.addOption(eq(7L), any())).thenReturn(ProductOptionGroupDto.builder().id(7L).build());

        mvc.perform(post("/api/v1/product-options/groups/7/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"name":"Red","defaultOption":false}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<OptionCreateRequest> captor = ArgumentCaptor.forClass(OptionCreateRequest.class);
        verify(service).addOption(eq(7L), captor.capture());
        OptionCreateRequest request = captor.getValue();
        assertThat(request.getName()).isEqualTo("Red");
        assertThat(request.isDefaultOption()).isFalse();
        // Omitted JSON property → null Integer; service treats null as 0.
        assertThat(request.getSortOrder()).isNull();
    }
}
