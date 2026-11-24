package com.opencrm.quote.lineitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencrm.quote.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LineItemQuantityBindingTest {

    private static final String LINES = "/api/v1/quotes/1/line-items";
    private static final String BASE_BODY = """
            {"productExternalId":"owned-product","listPrice":25
            """;

    private QuoteLineItemService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(QuoteLineItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new QuoteLineItemController(service))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "2147483648", "null"})
    void rejectsInvalidQuantityBeforeInvokingEitherServiceMethod(String value) throws Exception {
        String body = BASE_BODY + ",\"quantity\":" + value + "}";
        mvc.perform(post(LINES).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mvc.perform(put(LINES + "/2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void preservesDefaultAndExactIntegralQuantities() throws Exception {
        var response = QuoteLineItemDto.builder().id(2L).build();
        when(service.addLineItem(eq(1L), any())).thenReturn(response);
        when(service.updateLineItem(eq(1L), eq(2L), any())).thenReturn(response);

        mvc.perform(post(LINES).contentType(MediaType.APPLICATION_JSON).content(BASE_BODY + "}"))
                .andExpect(status().isCreated());
        mvc.perform(put(LINES + "/2").contentType(MediaType.APPLICATION_JSON)
                        .content(BASE_BODY + ",\"quantity\":3.0}"))
                .andExpect(status().isOk());

        var added = ArgumentCaptor.forClass(LineItemCreateRequest.class);
        var updated = ArgumentCaptor.forClass(LineItemCreateRequest.class);
        verify(service).addLineItem(eq(1L), added.capture());
        verify(service).updateLineItem(eq(1L), eq(2L), updated.capture());
        assertThat(added.getValue().getQuantity()).isEqualTo(1);
        assertThat(updated.getValue().getQuantity()).isEqualTo(3);
    }
}
