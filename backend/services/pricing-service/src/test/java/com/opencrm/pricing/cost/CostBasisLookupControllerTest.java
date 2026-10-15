package com.opencrm.pricing.cost;

import com.opencrm.pricing.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@DisplayName("GET /api/v1/cost-basis/products/{id}: the usable cost as a DTO, or 404")
class CostBasisLookupControllerTest {

    /** 23:30 UTC on 2026-10-15; "today" is the UTC date whatever the JVM's zone. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate Q4 = LocalDate.of(2026, 10, 1);

    private static final UnitCost SEAT = new UnitCost("PROD-SEAT", "seat", new BigDecimal("10.4800"), "USD", Q4, 1,
            Map.of(CostComponent.DIRECT, new BigDecimal("6.0800"),
                    CostComponent.COST_TO_SERVE, new BigDecimal("4.4000")));

    private CostBasisLookup lookup;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lookup = mock(CostBasisLookup.class);
        mvc = MockMvcBuilders.standaloneSetup(new CostBasisLookupController(lookup, CLOCK))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("both dates default to today (UTC) and the DTO carries cost, unit, currency, date, version, components")
    void defaultsToToday() throws Exception {
        when(lookup.find("PROD-SEAT", TODAY, TODAY)).thenReturn(Optional.of(SEAT));

        mvc.perform(get("/api/v1/cost-basis/products/PROD-SEAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productExternalId").value("PROD-SEAT"))
                .andExpect(jsonPath("$.data.unitCost").value(10.48))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.billingUnit").value("seat"))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-10-01"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.effectiveOn").value("2026-10-15"))
                .andExpect(jsonPath("$.data.pricedOn").value("2026-10-15"))
                .andExpect(jsonPath("$.data.components.length()").value(2))
                .andExpect(jsonPath("$.data.components[0].component").value("DIRECT"))
                .andExpect(jsonPath("$.data.components[0].unitCost").value(6.08))
                .andExpect(jsonPath("$.data.components[1].component").value("COST_TO_SERVE"))
                .andExpect(jsonPath("$.data.components[1].unitCost").value(4.40));
        verify(lookup).find("PROD-SEAT", TODAY, TODAY);
    }

    @Test
    @DisplayName("effectiveOn and pricedOn are passed through separately, in that order")
    void passesBothDates() throws Exception {
        LocalDate effectiveOn = LocalDate.of(2028, 10, 15);
        when(lookup.find("PROD-SEAT", effectiveOn, TODAY)).thenReturn(Optional.of(SEAT));

        mvc.perform(get("/api/v1/cost-basis/products/PROD-SEAT")
                        .param("effectiveOn", "2028-10-15").param("pricedOn", "2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveOn").value("2028-10-15"))
                .andExpect(jsonPath("$.data.pricedOn").value("2026-10-15"));
        verify(lookup).find("PROD-SEAT", effectiveOn, TODAY);
    }

    @Test
    @DisplayName("only effectiveOn given: pricedOn is still today, never the period start")
    void pricedOnDefaultsIndependently() throws Exception {
        LocalDate effectiveOn = LocalDate.of(2027, 10, 15);
        when(lookup.find("PROD-SEAT", effectiveOn, TODAY)).thenReturn(Optional.of(SEAT));

        mvc.perform(get("/api/v1/cost-basis/products/PROD-SEAT").param("effectiveOn", "2027-10-15"))
                .andExpect(status().isOk());
        verify(lookup).find("PROD-SEAT", effectiveOn, TODAY);
    }

    @Test
    @DisplayName("no usable cost (none, stale or inconsistent) is 404 COST_BASIS_NOT_FOUND")
    void notFound() throws Exception {
        when(lookup.find(anyString(), any(LocalDate.class), any(LocalDate.class))).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/cost-basis/products/GHOST").param("pricedOn", "2027-04-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COST_BASIS_NOT_FOUND"));
    }

    @Test
    @DisplayName("a date that is not ISO YYYY-MM-DD is 400 COST_BASIS_DATE_INVALID, never a lookup (and never a 500)")
    void malformedDate() throws Exception {
        mvc.perform(get("/api/v1/cost-basis/products/PROD-SEAT").param("effectiveOn", "15/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COST_BASIS_DATE_INVALID"));
        mvc.perform(get("/api/v1/cost-basis/products/PROD-SEAT").param("pricedOn", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COST_BASIS_DATE_INVALID"));
        org.mockito.Mockito.verifyNoInteractions(lookup);
    }
}
