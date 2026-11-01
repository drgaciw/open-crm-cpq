package com.opencrm.crm.outbox;

import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.crm.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL (from {@link BaseIntegrationTest}) and Kafka. The relay publishes the outbox row
 * and then marks it published.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "opencrm.outbox.relay.enabled=true")
@DisplayName("DRG-510: CRM outbox relay")
class CrmOutboxRelayIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("the relay publishes OrganizationCreated and marks the outbox row")
    void relayPublishesAndMarksTheRow() throws Exception {
        CrmOutboxHttp.Created created = http.createOrganization("Relay Org");
        relay.publishPendingEvents();

        ConsumerRecord<String, String> record = CrmKafkaSupport.awaitRecord(
                KAFKA, CrmOutboxTopics.ORGANIZATION_CREATED, created.uuid(), Duration.ofSeconds(60));

        assertThat(record.value()).contains(created.uuid()).contains("Relay Org");
        assertThat(CrmKafkaSupport.header(record, EventHeaders.EVENT_TYPE)).isEqualTo("OrganizationCreated");
        assertThat(CrmKafkaSupport.header(record, EventHeaders.EVENT_VERSION)).isEqualTo("1");
        assertThat(CrmKafkaSupport.header(record, EventHeaders.PRODUCER)).isEqualTo("crm-service");
        assertThat(CrmKafkaSupport.header(record, EventHeaders.EVENT_ID)).isNotBlank();
        assertThat(CrmKafkaSupport.header(record, EventHeaders.ACTOR)).isNotBlank();

        Instant deadline = Instant.now().plusSeconds(15);
        Map<String, Object> row = rows.forAggregate(created.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED).getFirst();
        while (Instant.now().isBefore(deadline) && !Boolean.TRUE.equals(row.get("published"))) {
            relay.publishPendingEvents();
            Thread.sleep(200);
            row = rows.forAggregate(created.uuid(), CrmOutboxTopics.ORGANIZATION_CREATED).getFirst();
        }
        assertThat(row.get("published")).isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(CrmKafkaSupport.header(record, EventHeaders.ACTOR)).isEqualTo(rows.actor(row));
    }
}
