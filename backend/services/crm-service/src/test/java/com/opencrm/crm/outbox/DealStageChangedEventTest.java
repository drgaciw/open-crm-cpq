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

@DisplayName("DRG-510: DealStageChanged outbox row")
class DealStageChangedEventTest extends BaseIntegrationTest {

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
    @DisplayName("advancing a deal writes exactly one DealStageChanged outbox row")
    void advanceWritesExactlyOneStageChangedRow() throws Exception {
        CrmOutboxHttp.Created created = http.createDeal("Stage move");

        http.advance(created.id());

        List<Map<String, Object>> written = rows.forAggregate(created.uuid(), CrmOutboxTopics.DEAL_STAGE_CHANGED);
        assertThat(written).hasSize(1);
        assertThat(rows.payload(written.getFirst()))
                .contains(created.uuid())
                .contains("PROSPECTING")
                .contains("QUALIFICATION");
        assertThat(written.getFirst().get("aggregate_type")).isEqualTo("Deal");
    }
}
