package com.opencrm.ai.audit;

import com.opencrm.ai.audit.entity.AiAuditLog;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class AiAuditPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void validatesMigratedSchemaAndRoundTripsJsonObjectsWithoutDoubleEncoding() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("ai").defaultSchema("ai").locations("classpath:db/migration").load().migrate();
        try (var factory = new Configuration().addAnnotatedClass(AiAuditLog.class)
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "validate").buildSessionFactory();
                var session = factory.openSession()) {
            var audit = new AiAuditLog();
            audit.setAgentId("synthetic-agent");
            audit.setSessionId("synthetic-session");
            audit.setEventType("INVOCATION");
            audit.setInputContext("{\"quoteId\":42}");
            audit.setSnapshotReferences("{\"catalog\":\"v1\",\"quote\":\"v2\"}");
            var transaction = session.beginTransaction();
            session.persist(audit);
            transaction.commit();
            session.clear();
            var restored = session.find(AiAuditLog.class, audit.getId());
            assertThat(restored.getInputContext()).contains("42");
            assertThat(restored.getSnapshotReferences()).contains("v1");
            assertThat(restored.getModel()).isEqualTo("unknown");
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT jsonb_typeof(input_context), input_context->>'quoteId', "
                            + "jsonb_typeof(snapshot_references), snapshot_references->>'catalog' FROM ai.agent_audit_log")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("object");
                assertThat(result.getString(2)).isEqualTo("42");
                assertThat(result.getString(3)).isEqualTo("object");
                assertThat(result.getString(4)).isEqualTo("v1");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
