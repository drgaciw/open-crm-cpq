package com.opencrm.quote.lineitem;

import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.config.QuoteLineConcurrencyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuoteLineConcurrencyAdviceTest {
    @org.springframework.web.bind.annotation.RestController
    static class UnrelatedController {
        @org.springframework.web.bind.annotation.GetMapping("/unrelated")
        void fail() { throw new ObjectOptimisticLockingFailureException(QuoteLineItem.class, 3L); }
    }
    private static final String BODY = "{\"productExternalId\":\"sku\",\"quantity\":1,\"listPrice\":10}";
    @Test void optimisticFailureIs409ButUnrelatedFailureRemains500() throws Exception {
        var service=mock(QuoteLineItemService.class);
        var quoteService=mock(com.opencrm.quote.quote.QuoteService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new QuoteLineItemController(service), new com.opencrm.quote.quote.QuoteController(quoteService), new UnrelatedController())
                .setControllerAdvice(new QuoteLineConcurrencyAdvice(), new GlobalExceptionHandler()).build();
        when(service.updateLineItem(eq(1L),eq(2L),any())).thenThrow(new ObjectOptimisticLockingFailureException(QuoteLineItem.class,2L));
        mvc.perform(put("/api/v1/quotes/1/line-items/2").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("QUOTE_VERSION_CONFLICT"));
        when(service.updateLineItem(eq(1L),eq(2L),any())).thenThrow(new IllegalStateException("unrelated"));
        mvc.perform(put("/api/v1/quotes/1/line-items/2").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));
        when(quoteService.calculate(1L)).thenThrow(new ObjectOptimisticLockingFailureException(com.opencrm.quote.quote.Quote.class,1L));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/quotes/1/calculate"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("QUOTE_VERSION_CONFLICT"));
        mvc.perform(get("/unrelated"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));
    }
}
