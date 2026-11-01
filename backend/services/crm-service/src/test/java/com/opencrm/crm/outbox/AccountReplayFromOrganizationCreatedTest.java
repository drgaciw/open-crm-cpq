package com.opencrm.crm.outbox;

import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replays the {@code OrganizationCreated} stream (the AccountCreated event of BC-12-01, written by DRG-510)
 * into a fresh projection keyed by {@link AccountId}. A second apply does not add accounts.
 * The projection equals {@code GET /api/v1/organizations}.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "opencrm.outbox.relay.enabled=true")
@DisplayName("DRG-481: replay OrganizationCreated into AccountId")
class AccountReplayFromOrganizationCreatedTest extends BaseIntegrationTest {

    static final KafkaContainer KAFKA = CrmKafkaSupport.startKafka();

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRelayPublisher relay;

    private CrmOutboxHttp http;
    private CrmOutboxRows rows;

    @BeforeEach
    void setUp() {
        http = new CrmOutboxHttp(mockMvc, objectMapper);
        rows = new CrmOutboxRows(jdbcTemplate);
    }

    @Test
    @DisplayName("replaying OrganizationCreated rebuilds the account set and a second replay adds nothing")
    void replayBuildsTheAccountSetWithoutDuplicates() throws Exception {
        CrmOutboxHttp.Created firstAccount = http.createOrganization("Replay Account A");
        CrmOutboxHttp.Created secondAccount = http.createOrganization("Replay Account B");
        relay.publishPendingEvents();

        List<ConsumerRecord<String, String>> firstRead = readStream("acct-replay-a", firstAccount, secondAccount);
        List<ConsumerRecord<String, String>> freshRead = readStream("acct-replay-b", firstAccount, secondAccount);

        assertThat(rows.forAggregate(firstAccount.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).hasSize(1);
        assertThat(rows.forAggregate(secondAccount.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED)).hasSize(1);

        Map<AccountId, String> once = project(firstRead);
        Map<AccountId, String> replayed = project(concat(firstRead, firstRead));
        Map<AccountId, String> fromFreshConsumer = project(freshRead);

        assertThat(once).hasSize(2);
        assertThat(replayed).isEqualTo(once);
        assertThat(fromFreshConsumer).isEqualTo(once);
        assertThat(once).isEqualTo(accountsFromApi());
    }

    @Test
    void contactEventUsesSameAccountUuidWhileRestKeepsNumericOrganizationId() throws Exception {
        CrmOutboxHttp.Created account = http.createOrganization("Contact account");
        String response = mockMvc.perform(post("/api/v1/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"organizationId\":" + account.id() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode contact = objectMapper.readTree(response).get("data");
        assertThat(contact.get("organizationId").asLong()).isEqualTo(account.id());
        var events = rows.forAggregate(contact.get("uuid").asString(), CrmOutboxTopics.CONTACT_CREATED);
        assertThat(events).hasSize(1);
        JsonNode event = objectMapper.readTree(rows.payload(events.getFirst()));
        assertThat(AccountId.of(event.get("organizationId").asString())).isEqualTo(AccountId.of(account.uuid()));
    }

    private List<ConsumerRecord<String, String>> readStream(
            String groupPrefix, CrmOutboxHttp.Created first, CrmOutboxHttp.Created second) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        records.addAll(CrmKafkaSupport.recordsForKey(
                KAFKA, CrmOutboxTopics.ORGANIZATION_CREATED, groupPrefix + "-" + first.uuid(),
                first.uuid(), Duration.ofSeconds(60)));
        records.addAll(CrmKafkaSupport.recordsForKey(
                KAFKA, CrmOutboxTopics.ORGANIZATION_CREATED, groupPrefix + "-" + second.uuid(),
                second.uuid(), Duration.ofSeconds(30)));
        return records;
    }

    private Map<AccountId, String> project(List<ConsumerRecord<String, String>> records) throws Exception {
        Map<AccountId, String> projection = new LinkedHashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            JsonNode payload = objectMapper.readTree(record.value());
            String uuid = payload.get("organizationId").asString();
            String name = payload.get("name").asString();
            AccountId accountId = AccountId.of(uuid);
            String view = uuid + "\n" + name;
            String existing = projection.putIfAbsent(accountId, view);
            if (existing != null) {
                assertThat(existing).isEqualTo(view);
            }
        }
        return projection;
    }

    private Map<AccountId, String> accountsFromApi() throws Exception {
        JsonNode data = objectMapper.readTree(mockMvc.perform(get("/api/v1/organizations").param("size", "100"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("data");
        Map<AccountId, String> accounts = new LinkedHashMap<>();
        for (JsonNode row : data) {
            AccountId accountId = AccountId.of(row.get("uuid").asString());
            accounts.put(accountId, row.get("uuid").asString() + "\n" + row.get("name").asString());
        }
        return accounts;
    }

    private static List<ConsumerRecord<String, String>> concat(
            List<ConsumerRecord<String, String>> left, List<ConsumerRecord<String, String>> right) {
        List<ConsumerRecord<String, String>> combined = new ArrayList<>(left.size() + right.size());
        combined.addAll(left);
        combined.addAll(right);
        return combined;
    }
}
