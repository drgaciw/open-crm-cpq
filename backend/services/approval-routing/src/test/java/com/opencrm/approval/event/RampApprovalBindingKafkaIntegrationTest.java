package com.opencrm.approval.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencrm.approval.ApprovalRoutingApplication;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** R2 canonical binding through the real listener, broker, database, Flyway and authenticated decision API. */
@Tag("integration")
@Testcontainers
@Import(TestJwtDecoderConfiguration.class)
@SpringBootTest(classes = ApprovalRoutingApplication.class, properties = {
        "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER,
        "spring.autoconfigure.exclude="
                + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
@DisplayName("R2 quote-owned ramp binding delivery and canonical decisions (Testcontainers)")
class RampApprovalBindingKafkaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_HEAP_OPTS", "-Xms128m -Xmx256m");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=approval");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired ApprovalRequestRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void securityApi() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @Test
    void quoteTopicDeliveryAndRedeliveryBindTheExactScheduleOnce() throws Exception {
        String quoteId = "ramp-it-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String first = binding(quoteId, "L-1", "a".repeat(64), 1, true);
        send(quoteId, first, eventId);
        await(quoteId, ApprovalStatus.PENDING, 1);
        ApprovalRequest original = one(quoteId, ApprovalStatus.PENDING);
        assertThat(original.getPricingFingerprint()).isEqualTo("a".repeat(64));
        assertThat(original.getGoverningPeriod()).isEqualTo(1);
        assertThat(original.getRequestedDiscount()).isEqualByComparingTo("0.32");
        assertThat(original.getApprovalTier()).isEqualTo("SALES_VP");

        send(quoteId, first, eventId); // Ack-timeout redelivery of the same event.
        send(quoteId, first, null); // Same binding from a legacy publisher without an event ID.
        send(quoteId, binding(quoteId, "L-1", "b".repeat(64), 1, true), UUID.randomUUID().toString());
        await(quoteId, ApprovalStatus.SUPERSEDED, 1); // Same-partition marker proves duplicates were processed first.
        assertThat(all(quoteId)).hasSize(2);
        assertThat(one(quoteId, ApprovalStatus.SUPERSEDED).getId()).isEqualTo(original.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE consumer_group = ? AND event_id = ?",
                Integer.class, DiscountApprovalEventConsumer.CONSUMER_GROUP, eventId)).isEqualTo(1);
    }

    @Test
    void unchangedMaximumDiscountButChangedScheduleSupersedesAndLateDecisionsReturn409() throws Exception {
        String quoteId = "ramp-it-" + UUID.randomUUID();
        send(quoteId, binding(quoteId, "L-1", "c".repeat(64), 1, true), UUID.randomUUID().toString());
        await(quoteId, ApprovalStatus.PENDING, 1);
        ApprovalRequest original = one(quoteId, ApprovalStatus.PENDING);
        // Maximum discount remains 32%; the governing line and exact schedule binding change.
        send(quoteId, binding(quoteId, "L-2", "d".repeat(64), 2, true), UUID.randomUUID().toString());
        await(quoteId, ApprovalStatus.SUPERSEDED, 1);
        ApprovalRequest current = one(quoteId, ApprovalStatus.PENDING);
        ApprovalRequest replaced = one(quoteId, ApprovalStatus.SUPERSEDED);
        assertThat(current.getLineId()).isEqualTo("L-2");
        assertThat(current.getGoverningPeriod()).isEqualTo(2);
        assertThat(current.getRequestedDiscount()).isEqualByComparingTo(original.getRequestedDiscount());
        assertThat(replaced.getSupersededBy()).isEqualTo(current.getId());

        mvc.perform(post("/api/v1/approvals/{id}/approve", replaced.getId())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("rep-late", "CPQ.User").bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("APPROVER_ROLE_REQUIRED"));
        mvc.perform(post("/api/v1/approvals/{id}/approve", replaced.getId())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("vp-late", "SalesVP").bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("APPROVAL_SUPERSEDED"));
        mvc.perform(post("/api/v1/approvals/{id}/reject", replaced.getId()).param("reason", "Late decision")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("vp-late", "SalesVP").bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("APPROVAL_SUPERSEDED"));
        assertThat(one(quoteId, ApprovalStatus.PENDING).getId()).isEqualTo(current.getId());
        mvc.perform(post("/api/v1/approvals/{id}/approve", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("vp-current", "SalesVP").bearer()))
                .andExpect(status().isOk());
        assertThat(one(quoteId, ApprovalStatus.APPROVED).getApproverId()).isEqualTo(TestTokens.oidOf("vp-current"));
    }

    @Test
    void retirementCancelsTheCurrentBindingAndFreshPricingCreatesANewRequest() throws Exception {
        String quoteId = "ramp-it-" + UUID.randomUUID();
        send(quoteId, binding(quoteId, "L-1", "e".repeat(64), 1, true), UUID.randomUUID().toString());
        await(quoteId, ApprovalStatus.PENDING, 1);
        Long oldId = one(quoteId, ApprovalStatus.PENDING).getId();
        String retirementId = UUID.randomUUID().toString();
        String retirement = binding(quoteId, "L-1", "f".repeat(64), 1, false);
        send(quoteId, retirement, retirementId);
        await(quoteId, ApprovalStatus.CANCELLED, 1);
        assertThat(repository.findByQuoteIdAndStatus(quoteId, ApprovalStatus.PENDING)).isEmpty();
        assertThat(one(quoteId, ApprovalStatus.SUPERSEDED).getId()).isEqualTo(oldId);
        send(quoteId, retirement, retirementId);
        send(quoteId, binding(quoteId, "L-1", "1".repeat(64), 1, true), UUID.randomUUID().toString());
        await(quoteId, ApprovalStatus.PENDING, 1);
        assertThat(all(quoteId)).hasSize(3);
        assertThat(one(quoteId, ApprovalStatus.PENDING).getPricingFingerprint()).isEqualTo("1".repeat(64));
    }

    private static String binding(String quoteId, String lineId, String fingerprint, int period, boolean required) {
        return """
                {"quoteId":"%s","lineId":"%s","marginDelta":0,"requestedDiscount":0.32,"currencyCode":"USD",
                 "pricingFingerprint":"%s","governingPeriod":%d,"approvalTier":"SALES_VP",
                 "reason":"Ramp period %d: RAMP_DISCOUNT_THRESHOLD","approvalRequired":%s}
                """.formatted(quoteId, lineId, fingerprint, period, period, required);
    }

    private List<ApprovalRequest> all(String quoteId) {
        return repository.findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(quoteId);
    }

    private ApprovalRequest one(String quoteId, ApprovalStatus status) {
        List<ApprovalRequest> rows = repository.findByQuoteIdAndStatus(quoteId, status);
        assertThat(rows).as("%s requests for %s", status, quoteId).hasSize(1);
        return rows.getFirst();
    }

    private void await(String quoteId, ApprovalStatus status, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(90);
        while (Instant.now().isBefore(deadline)) {
            if (repository.findByQuoteIdAndStatus(quoteId, status).size() == expected) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Expected " + expected + " " + status + " requests for " + quoteId);
    }

    private static void send(String quoteId, String payload, String eventId) throws Exception {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // One partition, one quote key: the changed binding is a deterministic processing barrier.
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(KafkaTopics.RAMP_APPROVAL_BINDING_CHANGED,
                    0, quoteId, payload);
            if (eventId != null) record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        }
    }
}
