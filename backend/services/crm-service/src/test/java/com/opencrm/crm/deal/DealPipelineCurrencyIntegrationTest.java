package com.opencrm.crm.deal;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DRG-509 / ADR-032: pipeline totals are in one base currency and never add amounts in different currencies.
 */
@DisplayName("Deal pipeline currency (ADR-032)")
@Tag("integration")
class DealPipelineCurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DealRepository dealRepository;

    @BeforeEach
    void clearRates() {
        jdbcTemplate.update("DELETE FROM crm.fx_rates");
    }

    private void rate(String from, String to, String rate, Instant asOf) {
        jdbcTemplate.update("INSERT INTO crm.fx_rates (from_currency, to_currency, rate, as_of, source) VALUES (?, ?, ?, ?, 'MANUAL')",
                from, to, new BigDecimal(rate), Timestamp.from(asOf));
    }

    private long createDeal(String name, String amount, String currency) throws Exception {
        DealCreateRequest request = DealCreateRequest.builder()
                .name(name).amount(new BigDecimal(amount)).currency(currency).build();
        String body = mockMvc.perform(post("/api/v1/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private JsonNode prospectingRow() throws Exception {
        String body = mockMvc.perform(get("/api/v1/deals/pipeline"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode row : objectMapper.readTree(body).get("data")) {
            if ("PROSPECTING".equals(row.get("stage").asString())) {
                return row;
            }
        }
        throw new AssertionError("no PROSPECTING row in " + body);
    }

    @Test
    @DisplayName("EUR 1,000,000 + USD 1,000,000 is reported in USD at the frozen rate, not as 2,000,000")
    void mixedCurrenciesAreConvertedNotAdded() throws Exception {
        rate("EUR", "USD", "1.0850000000", Instant.now().minus(1, ChronoUnit.DAYS));

        createDeal("Euro deal", "1000000", "EUR");
        createDeal("Dollar deal", "1000000", "USD");

        JsonNode row = prospectingRow();
        assertThat(row.get("count").asLong()).isEqualTo(2);
        assertThat(row.get("baseCurrency").asString()).isEqualTo("USD");
        assertThat(row.get("totalAmount").decimalValue()).isEqualByComparingTo("2085000.00");
        // probability 10 at PROSPECTING
        assertThat(row.get("weightedAmount").decimalValue()).isEqualByComparingTo("208500.00");
        assertThat(row.get("unconvertedCount").asLong()).isZero();
    }

    @Test
    @DisplayName("a deal with no rate is excluded from totals and counted as unconverted (D-04)")
    void missingRateIsExcludedAndCounted() throws Exception {
        long gbp = createDeal("Sterling deal", "500000", "GBP");
        createDeal("Dollar deal", "1000", "USD");

        JsonNode row = prospectingRow();
        assertThat(row.get("count").asLong()).isEqualTo(2);
        assertThat(row.get("totalAmount").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(row.get("unconvertedCount").asLong()).isEqualTo(1);
        assertThat(dealRepository.findById(gbp).orElseThrow().getBaseAmount()).isNull();
    }

    @Test
    @DisplayName("an unconverted deal converts on its next edit once a rate exists")
    void unconvertedDealConvertsOnNextEdit() throws Exception {
        long gbp = createDeal("Sterling deal", "100", "GBP");
        rate("GBP", "USD", "1.25", Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(put("/api/v1/deals/{id}", gbp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"touched\"}"))
                .andExpect(status().isOk());

        Deal deal = dealRepository.findById(gbp).orElseThrow();
        assertThat(deal.getBaseAmount()).isEqualByComparingTo("125.00");
        assertThat(deal.getFxSource()).isEqualTo("MANUAL");
        assertThat(prospectingRow().get("unconvertedCount").asLong()).isZero();
    }

    @Test
    @DisplayName("D-03: the rate frozen at close does not move when a newer rate is loaded")
    void closedDealKeepsItsRate() throws Exception {
        rate("EUR", "USD", "1.10", Instant.now().minus(2, ChronoUnit.DAYS));
        long eur = createDeal("Euro deal", "1000", "EUR");
        recordAcceptedQuote(eur);
        mockMvc.perform(post("/api/v1/deals/{id}/win", eur)).andExpect(status().isOk());

        rate("EUR", "USD", "1.50", Instant.now().minus(1, ChronoUnit.SECONDS));

        Deal deal = dealRepository.findById(eur).orElseThrow();
        assertThat(deal.getStage()).isEqualTo(DealStage.WON);
        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1100.00");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1.10");
    }

    @Test
    @DisplayName("a later edit of a won deal does not re-freeze, and amount or currency changes are rejected")
    void wonDealEditDoesNotRefreeze() throws Exception {
        rate("EUR", "USD", "1.10", Instant.now().minus(2, ChronoUnit.DAYS));
        long eur = createDeal("Euro deal", "1000", "EUR");
        recordAcceptedQuote(eur);
        mockMvc.perform(post("/api/v1/deals/{id}/win", eur)).andExpect(status().isOk());

        jdbcTemplate.update("""
                UPDATE crm.deals
                   SET base_amount = NULL,
                       base_currency = 'EUR',
                       fx_rate = NULL,
                       fx_source = NULL
                 WHERE id = ?
                """, eur);
        rate("EUR", "USD", "1.50", Instant.now().minus(1, ChronoUnit.SECONDS));

        mockMvc.perform(put("/api/v1/deals/{id}", eur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"note after close\"}"))
                .andExpect(status().isOk());

        Deal afterNote = dealRepository.findById(eur).orElseThrow();
        assertThat(afterNote.getDescription()).isEqualTo("note after close");
        assertThat(afterNote.getAmount()).isEqualByComparingTo("1000");
        assertThat(afterNote.getCurrency()).isEqualTo("EUR");
        assertThat(afterNote.getBaseAmount()).isNull();
        assertThat(afterNote.getBaseCurrency()).isEqualTo("EUR");

        mockMvc.perform(put("/api/v1/deals/{id}", eur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":2000,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());

        Deal afterReject = dealRepository.findById(eur).orElseThrow();
        assertThat(afterReject.getAmount()).isEqualByComparingTo("1000");
        assertThat(afterReject.getCurrency()).isEqualTo("EUR");
        assertThat(afterReject.getBaseAmount()).isNull();
        assertThat(afterReject.getBaseCurrency()).isEqualTo("EUR");
    }

    private void recordAcceptedQuote(long dealId) {
        Deal deal = dealRepository.findById(dealId).orElseThrow();
        deal.setAcceptedQuoteId("accepted-quote-" + dealId);
        dealRepository.saveAndFlush(deal);
    }
}
