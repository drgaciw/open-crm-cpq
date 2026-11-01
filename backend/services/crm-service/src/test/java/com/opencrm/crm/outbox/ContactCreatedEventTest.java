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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DRG-510: ContactCreated outbox row")
class ContactCreatedEventTest extends BaseIntegrationTest {

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
    @DisplayName("POST /api/v1/contacts writes exactly one ContactCreated outbox row")
    void postContactWritesExactlyOneOutboxRow() throws Exception {
        String email = "ada-" + UUID.randomUUID() + "@example.com";
        CrmOutboxHttp.Created created = http.createContact("Ada", email);

        List<Map<String, Object>> written = rows.forAggregate(created.uuid(), CrmOutboxTopics.CONTACT_CREATED);

        assertThat(written).hasSize(1);
        assertThat(rows.payload(written.getFirst())).contains(created.uuid()).contains(email);
        assertThat(written.getFirst().get("aggregate_type")).isEqualTo("Contact");
    }
}
