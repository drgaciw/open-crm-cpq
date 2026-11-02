package com.opencrm.crm.outbox;

import com.opencrm.crm.BaseIntegrationTest;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
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

/**
 * ADR-016 step 4 actor envelope on each CRM event. A person is {@code user:<oid>}. An agent acting as itself
 * is an app-only token ({@code idtyp=app}) and is recorded {@code service:<client-id>}, the actor header ADR-016
 * step 4 defines for an application. The relay copies this column to the Kafka {@code actor} header.
 */
@DisplayName("DRG-510: CRM event actor headers")
class CrmEventActorHeadersTest extends BaseIntegrationTest {

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

    @AfterEach
    void clearActor() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("a user actor header is present on each envelope")
    void userActorOnEachEnvelope() throws Exception {
        TestCallers.signInUser("alice", "CPQ.User");
        assertActorOnEachEnvelope("user:" + TestTokens.oidOf("alice"));
    }

    @Test
    @DisplayName("an agent actor header is present on each envelope")
    void agentActorOnEachEnvelope() throws Exception {
        String actor = TestCallers.signInService("crm-agent", "CPQ.User");
        assertThat(actor).isEqualTo("service:crm-agent");
        assertActorOnEachEnvelope(actor);
    }

    private void assertActorOnEachEnvelope(String actor) throws Exception {
        String suffix = UUID.randomUUID().toString();
        CrmOutboxHttp.Created organization = http.createOrganization("Actor Org " + suffix);
        CrmOutboxHttp.Created contact = http.createContact("Actor", "actor-" + suffix + "@example.com");
        CrmOutboxHttp.Created deal = http.createDeal("Actor deal " + suffix);
        http.advance(deal.id());
        jdbcTemplate.update(
                "UPDATE crm.deals SET accepted_quote_id = ? WHERE id = ?",
                "accepted-quote-" + deal.id(),
                deal.id());

        assertThat(actorOf(organization.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).isEqualTo(actor);
        assertThat(actorOf(contact.uuid(), CrmOutboxTopics.CONTACT_CREATED)).isEqualTo(actor);
        assertThat(actorOf(deal.uuid(), CrmOutboxTopics.DEAL_STAGE_CHANGED)).isEqualTo(actor);

        http.win(deal.id());
        assertThat(actorOf(deal.uuid(), CrmOutboxTopics.DEAL_CLOSED)).isEqualTo(actor);
    }

    private String actorOf(String aggregateId, String topic) {
        List<Map<String, Object>> written = rows.forAggregate(aggregateId, topic);
        assertThat(written).as(topic).hasSize(1);
        return rows.actor(written.getFirst());
    }
}
