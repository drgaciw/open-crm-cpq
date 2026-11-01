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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA payloads for New option group omit {@code sortOrder}. Jackson must still bind
 * {@link OptionGroupCreateRequest} (defaults to 0) so Create does not 400 INVALID_REQUEST_BODY.
 */
class OptionGroupCreateHttpContractTest {

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
    @DisplayName("SPA payload without sortOrder creates group with sortOrder default 0")
    void spaPayloadWithoutSortOrderCreatesGroup() throws Exception {
        when(service.createGroup(any())).thenReturn(ProductOptionGroupDto.builder().id(1L).build());

        mvc.perform(post("/api/v1/product-options/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"name":"Features","selectionType":"SINGLE","minSelections":0,"maxSelections":3}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<OptionGroupCreateRequest> captor = ArgumentCaptor.forClass(OptionGroupCreateRequest.class);
        verify(service).createGroup(captor.capture());
        OptionGroupCreateRequest request = captor.getValue();
        assertThat(request.getProductId()).isEqualTo(1L);
        assertThat(request.getName()).isEqualTo("Features");
        assertThat(request.getSelectionType()).isEqualTo(SelectionType.SINGLE);
        assertThat(request.getMinSelections()).isEqualTo(0);
        assertThat(request.getMaxSelections()).isEqualTo(3);
        // Omitted JSON property → null Integer; entity mapping treats null as 0.
        assertThat(request.getSortOrder()).isNull();
    }
}
