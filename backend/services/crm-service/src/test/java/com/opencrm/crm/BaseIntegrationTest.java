package com.opencrm.crm;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests.
 * Shared Testcontainers PostgreSQL + Redis (RedisMessageListenerContainer requires a live Redis).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Tag("docker")
@Tag("integration")
public abstract class BaseIntegrationTest {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("opencrm")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/init-test.sql");
        POSTGRES.start();

        @SuppressWarnings("resource")
        GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
        REDIS = redis;
        REDIS.start();
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Testcontainers may already append query params (e.g. loggerLevel=OFF); use & not a second ?.
        registry.add("spring.datasource.url", () -> JdbcUrls.appendQueryParam(POSTGRES.getJdbcUrl(), "currentSchema", "crm"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.cloud.azure.compatibility-verifier.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () ->
                "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                        + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                        // crm-service has Kafka since ADR-018 D8; azure-spring 5.x's Event Hubs Kafka config cannot load on Boot 4.
                        + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration");
    }

    @BeforeEach
    void cleanupDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "TRUNCATE TABLE crm.leads, crm.deals, crm.contacts, crm.organizations RESTART IDENTITY CASCADE"
            ).executeUpdate();
            entityManager.flush();
            entityManager.clear();
        });
    }
}
