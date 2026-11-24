package com.opencrm.pricing.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.config.GlobalExceptionHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = CustomerAuditUpdateIntegrationTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "server.address=127.0.0.1",
                "opencrm.security.mode=dev",
                "opencrm.kafka.inbox.enabled=false",
                "opencrm.outbox.relay.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.autoconfigure.exclude="
                        + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
class CustomerAuditUpdateIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=pricing");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = Customer.class)
    @EnableJpaRepositories(basePackageClasses = CustomerRepository.class)
    @Import({CustomerController.class, CustomerService.class, CustomerMapperImpl.class, GlobalExceptionHandler.class})
    static class App {}

    @LocalServerPort
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void updateReturnsThePersistedAuditVersionAndTimestamp() throws Exception {
        String base = "/api/v1/pricing-customers";
        String externalId = "audit-update-" + UUID.randomUUID();
        JsonNode created = data(send("POST", base, """
                {"externalId":"%s","name":"Original","segment":"SMB","region":"EMEA",
                 "churnScore":7,"annualRevenue":64129}
                """.formatted(externalId)), 201);
        String path = base + "/" + created.get("id").asLong();
        try {
            JsonNode before = data(send("GET", path, null), 200);
            JsonNode updated = data(send("PUT", path, """
                    {"name":"Revised","segment":"ENTERPRISE","region":"APAC","churnScore":0,"annualRevenue":0}
                    """), 200);
            JsonNode persisted = data(send("GET", path, null), 200);

            for (JsonNode customer : new JsonNode[]{updated, persisted}) {
                assertThat(customer.get("externalId").asText()).isEqualTo(externalId);
                assertThat(customer.get("name").asText()).isEqualTo("Revised");
                assertThat(customer.get("segment").asText()).isEqualTo("ENTERPRISE");
                assertThat(customer.get("region").asText()).isEqualTo("APAC");
                assertThat(customer.get("churnScore").decimalValue()).isZero();
                assertThat(customer.get("annualRevenue").decimalValue()).isZero();
            }

            assertThat(persisted.get("version").asLong()).isGreaterThan(before.get("version").asLong());
            assertThat(updated.get("version").asLong()).as("PUT version matches persisted version")
                    .isEqualTo(persisted.get("version").asLong());
            Instant returnedAt = Instant.parse(updated.get("updatedAt").asText());
            Instant persistedAt = Instant.parse(persisted.get("updatedAt").asText());
            assertThat(returnedAt).isAfter(Instant.parse(before.get("updatedAt").asText()));
            assertThat(returnedAt).isCloseTo(persistedAt, within(1, ChronoUnit.MICROS));
        } finally {
            assertThat(send("DELETE", path, null).statusCode()).isEqualTo(204);
            assertThat(send("GET", path, null).statusCode()).isEqualTo(404);
        }
    }

    private HttpResponse<String> send(String method, String path, String payload) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();
        if (payload != null) {
            request.header("Content-Type", "application/json");
            body = HttpRequest.BodyPublishers.ofString(payload);
        }
        return client.send(request.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        return json.readTree(response.body()).get("data");
    }
}
