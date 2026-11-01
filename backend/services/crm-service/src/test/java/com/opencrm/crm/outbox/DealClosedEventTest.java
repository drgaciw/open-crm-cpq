package com.opencrm.crm.outbox;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.crm.event.QuoteAcceptedDealProcessor;
import com.opencrm.crm.event.QuoteAcceptedOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

/**
 * DealClosed is pipeline state (won or lost). The payload carries no amount, currency, discount or approval,
 * so it is not a pricing or approval decision.
 */
@DisplayName("DRG-510: DealClosed outbox row")
class DealClosedEventTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;

    private CrmOutboxHttp http;
    private CrmOutboxRows rows;

    @BeforeEach
    void setUp() {
        http = new CrmOutboxHttp(mockMvc, objectMapper);
        rows = new CrmOutboxRows(jdbcTemplate);
    }

    @Test
    @DisplayName("winning a deal writes exactly one DealClosed row and no pricing or approval fields")
    void winWritesExactlyOneClosedRowWithoutPricing() throws Exception {
        CrmOutboxHttp.Created created = http.createDeal("Won deal");
        jdbcTemplate.update(
                "UPDATE crm.deals SET accepted_quote_id = ? WHERE id = ?",
                "accepted-quote-" + created.id(),
                created.id());

        http.win(created.id());

        assertClosed(created.uuid(), "WON", null);
    }

    @Test
    @DisplayName("losing a deal writes exactly one DealClosed row with the loss reason and no pricing fields")
    void loseWritesExactlyOneClosedRowWithoutPricing() throws Exception {
        CrmOutboxHttp.Created created = http.createDeal("Lost deal");

        http.lose(created.id(), "timing");

        assertClosed(created.uuid(), "LOST", "timing");
    }

    @Test
    void acceptingQuoteAfterLossOmitsTheOldLossReasonFromWonEvent() throws Exception {
        CrmOutboxHttp.Created created = http.createDeal("Reclosed deal");
        http.lose(created.id(), "timing");
        QuoteAcceptedCrmMessage message = QuoteAcceptedCrmMessage.builder()
                .quoteUuid(UUID.randomUUID().toString())
                .dealExternalId(created.uuid())
                .build();

        assertThat(quoteAcceptedDealProcessor.applyWinFromQuote(message, created.uuid()))
                .isEqualTo(QuoteAcceptedOutcome.APPLIED);
        var events = rows.forAggregate(created.uuid(), CrmOutboxTopics.DEAL_CLOSED);
        assertThat(events).hasSize(2);
        var won = events.stream().map(rows::payload)
                .map(payload -> objectMapper.readTree(payload))
                .filter(payload -> "WON".equals(payload.get("outcome").asString()))
                .findFirst().orElseThrow();
        assertThat(won.get("previousStage").asString()).isEqualTo("LOST");
        assertThat(won.hasNonNull("lostReason")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT lost_reason FROM deals WHERE id = ?", String.class, created.id()))
                .isEqualTo("timing");
    }

    @Test
    void updateToWonFreezesEditedAmountAndPublishesThePersistedCloseTime() throws Exception {
        assertUpdateCloses("WON", 100, "wonAt");
    }

    @Test
    void updateToLostFreezesEditedAmountAndPublishesThePersistedCloseTime() throws Exception {
        assertUpdateCloses("LOST", 0, "lostAt");
    }

    private void assertUpdateCloses(String stage, int probability, String timeField) throws Exception {
        CrmOutboxHttp.Created created = http.createDeal("Closed by update");
        String response = mockMvc.perform(put("/api/v1/deals/{id}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"" + stage + "\",\"amount\":1300,\"currency\":\"USD\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var deal = objectMapper.readTree(response).get("data");
        assertThat(deal.get("stage").asString()).isEqualTo(stage);
        assertThat(deal.get("probability").asInt()).isEqualTo(probability);
        assertThat(deal.get(timeField).asString()).isNotBlank();
        assertThat(deal.get("actualCloseDate").asString()).isNotBlank();
        assertThat(deal.get("baseAmount").decimalValue()).isEqualByComparingTo("1300.00");

        var written = rows.forAggregate(created.uuid(), CrmOutboxTopics.DEAL_CLOSED);
        assertThat(written).hasSize(1);
        assertThat(rows.forAggregate(created.uuid(), CrmOutboxTopics.DEAL_STAGE_CHANGED)).hasSize(1);
        var payload = objectMapper.readTree(rows.payload(written.getFirst()));
        assertThat(payload.get("outcome").asString()).isEqualTo(stage);
        assertThat(payload.get("closedAt").asString()).isEqualTo(deal.get(timeField).asString());
        if ("LOST".equals(stage)) {
            assertThat(payload.hasNonNull("lostReason")).isFalse();
        }
    }

    private void assertClosed(String dealUuid, String outcome, String lostReason) {
        List<Map<String, Object>> written = rows.forAggregate(dealUuid, CrmOutboxTopics.DEAL_CLOSED);
        assertThat(written).hasSize(1);
        String payload = rows.payload(written.getFirst());
        assertThat(payload).contains(dealUuid).contains(outcome).contains("PROSPECTING");
        if (lostReason != null) {
            assertThat(payload).contains(lostReason);
        }
        assertThat(payload).doesNotContain("1000", "USD", "amount", "currency", "discount", "approval", "price");
        assertThat(written.getFirst().get("aggregate_type")).isEqualTo("Deal");
    }
}
