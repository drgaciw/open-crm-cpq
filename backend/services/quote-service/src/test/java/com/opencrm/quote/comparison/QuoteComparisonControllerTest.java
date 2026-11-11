package com.opencrm.quote.comparison;

import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.quote.Quote;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real binding, service and advice; authenticated fixtures are not a full security-chain test. */
class QuoteComparisonControllerTest {
    private final QuoteComparisonRepository quotes = mock(QuoteComparisonRepository.class);
    private final QuotePrimarySelectionRepository selections = mock(QuotePrimarySelectionRepository.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new QuoteComparisonController(new QuoteComparisonService(quotes, selections)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
        Quote anchor = Quote.builder().id(7L).name("Good").quoteNumber("Q-7").tenantId("workforce")
                .customerExternalId("account").dealExternalId("deal").build();
        when(quotes.findByIdAndTenantIdAndDeletedFalse(7L, "workforce")).thenReturn(Optional.of(anchor));
        when(quotes.lockAnchor(7L, "workforce")).thenReturn(Optional.of(anchor));
        when(quotes.alternatives(eq("workforce"), eq("deal"), any(Pageable.class))).thenReturn(List.of(anchor));
        when(selections.selectedQuote("workforce", "deal")).thenReturn(Optional.of(7L));
        QuoteComparisonServiceTest.user("workforce");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void returnsEnvelopeAndExplicitUnknownMargin() throws Exception {
        mvc.perform(get("/api/v1/quotes/7/comparison"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dealExternalId").value("deal"))
                .andExpect(jsonPath("$.data.primaryQuoteId").value(7))
                .andExpect(jsonPath("$.data.quotes[0].margin.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.quotes[0].margin.value").doesNotExist())
                .andExpect(jsonPath("$.data.tenantId").doesNotExist());
    }

    @Test void primaryActionTakesScopeAndActorFromServerRatherThanRequest() throws Exception {
        mvc.perform(post("/api/v1/quotes/7/primary").contentType("application/json")
                        .content("{\"tenantId\":\"foreign\",\"dealExternalId\":\"foreign\",\"actor\":\"spoofed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primaryQuoteId").value(7));
        verify(selections).select("workforce", "deal", 7L, "seller");
    }

    @Test void unauthenticatedAndForeignRequestsAreRefused() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/quotes/7/comparison")).andExpect(status().isUnauthorized());
        QuoteComparisonServiceTest.user("foreign");
        mvc.perform(post("/api/v1/quotes/7/primary")).andExpect(status().isNotFound());
        verify(selections, never()).select(anyString(), anyString(), anyLong(), anyString());
    }
}
