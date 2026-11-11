package com.opencrm.quote.comparison;

import com.opencrm.quote.quote.QuoteDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComparisonControllerTest {
    @Test void compareAcceptsOpportunityAndQuoteFilter() throws Exception {
        var service = mock(ComparisonService.class);
        when(service.compare("deal-1", List.of(1L, 2L)))
                .thenReturn(new QuoteComparisonDto("deal-1", 1L, List.of(), List.of("marginAmount")));
        MockMvcBuilders.standaloneSetup(new ComparisonController(service)).build()
                .perform(get("/api/v1/quotes/compare").param("dealExternalId", "deal-1").param("quoteIds", "1,2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baselineQuoteId").value(1))
                .andExpect(jsonPath("$.data.dealExternalId").value("deal-1"));
    }

    @Test void missingOpportunityIsBadRequest() throws Exception {
        var service = mock(ComparisonService.class);
        MockMvcBuilders.standaloneSetup(new ComparisonController(service)).build()
                .perform(get("/api/v1/quotes/compare")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void primaryEndpointReturnsDtoNotEntity() throws Exception {
        var service = mock(ComparisonService.class);
        when(service.markPrimary(2L)).thenReturn(QuoteDto.builder().id(2L).primary(true).build());
        MockMvcBuilders.standaloneSetup(new ComparisonController(service)).build()
                .perform(post("/api/v1/quotes/2/primary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primary").value(true))
                .andExpect(jsonPath("$.data.id").value(2));
    }
}
