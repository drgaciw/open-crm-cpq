package com.opencrm.crm.outbox;

import com.opencrm.crm.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
