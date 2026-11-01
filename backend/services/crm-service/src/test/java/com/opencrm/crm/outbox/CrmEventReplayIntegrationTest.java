package com.opencrm.crm.outbox;

import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.crm.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the outbox stream to a fresh consumer. Both consumers see the same records, and applying the stream
 * twice (same {@code eventId}) does not duplicate the result.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "opencrm.outbox.relay.enabled=true")
@DisplayName("DRG-510: CRM outbox replay")
class CrmEventReplayIntegrationTest extends BaseIntegrationTest {

    private static final String CONSUMER = "crm-event-replay";

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
    private OutboxRelayPublisher relay;

    @Autowired
    private KafkaInbox inbox;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private CrmOutboxHttp http;

    @BeforeEach
    void setUp() {
        http = new CrmOutboxHttp(mockMvc, objectMapper);
    }

    @Test
    @DisplayName("a fresh consumer reads the same stream and a second apply does not duplicate")
    void freshConsumerSeesTheSameStreamWithoutDuplicates() throws Exception {
        CrmOutboxHttp.Created created = http.createOrganization("Replay Org");
        relay.publishPendingEvents();

        List<ConsumerRecord<String, String>> first = CrmKafkaSupport.recordsForKey(
                KAFKA, CrmOutboxTopics.ORGANIZATION_CREATED, "replay-a-" + created.uuid(),
                created.uuid(), Duration.ofSeconds(60));
        List<ConsumerRecord<String, String>> fresh = CrmKafkaSupport.recordsForKey(
                KAFKA, CrmOutboxTopics.ORGANIZATION_CREATED, "replay-b-" + created.uuid(),
                created.uuid(), Duration.ofSeconds(30));

        assertThat(first).hasSize(1);
        assertThat(fresh).hasSize(1);
        assertThat(eventId(fresh.getFirst())).isEqualTo(eventId(first.getFirst()));
        assertThat(fresh.getFirst().value()).isEqualTo(first.getFirst().value());

        Map<String, String> projection = new LinkedHashMap<>();
        assertThat(apply(first.getFirst(), projection)).isTrue();
        assertThat(apply(fresh.getFirst(), projection)).isFalse();
        assertThat(projection).hasSize(1);
        assertThat(projection).containsKey(eventId(first.getFirst()));
        assertThat(projection.get(eventId(first.getFirst()))).contains(created.uuid());
    }

    private boolean apply(ConsumerRecord<String, String> record, Map<String, String> projection) {
        Boolean claimed = transactionTemplate.execute(status -> {
            boolean firstTime = inbox.claim(CONSUMER, record);
            if (firstTime) {
                projection.put(eventId(record), record.value());
            }
            return firstTime;
        });
        return Boolean.TRUE.equals(claimed);
    }

    private static String eventId(ConsumerRecord<String, String> record) {
        return CrmKafkaSupport.header(record, EventHeaders.EVENT_ID);
    }
}
