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
 * Closes BC-12-01 "AccountCreated event is emitted": {@code OrganizationCreated} is that event.
 */
@DisplayName("DRG-510: OrganizationCreated outbox row")
class OrganizationCreatedEventTest extends BaseIntegrationTest {

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
    @DisplayName("POST /api/v1/organizations writes exactly one OrganizationCreated outbox row")
    void postOrganizationWritesExactlyOneOutboxRow() throws Exception {
        CrmOutboxHttp.Created created = http.createOrganization("Account Created Co");

        List<Map<String, Object>> written = rows.forAggregate(created.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED);

        assertThat(written).hasSize(1);
        assertThat(rows.payload(written.getFirst())).contains(created.uuid()).contains("Account Created Co");
        assertThat(written.getFirst().get("aggregate_type")).isEqualTo("Organization");
    }
}
