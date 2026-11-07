package com.opencrm.ai.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class AiSchemaMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void unknownModelDefaultPreservesHistoricRowsAndMigrationChecksums() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("ai").defaultSchema("ai").locations("classpath:db/migration").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO ai.agent_audit_log(agent_id,session_id,event_type) VALUES ('before','s','INVOCATION')");
            var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("ai").defaultSchema("ai").locations("classpath:db/migration").target("2").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            sql.executeUpdate("INSERT INTO ai.agent_audit_log(agent_id,session_id,event_type) VALUES ('after','s','INVOCATION')");
            try (var result = sql.executeQuery("SELECT agent_id,model FROM ai.agent_audit_log ORDER BY agent_id")) {
                assertThat(result.next()).isTrue(); assertThat(result.getString(2)).isEqualTo("unknown");
                assertThat(result.next()).isTrue(); assertThat(result.getString(2)).isEqualTo("glm-5");
            }
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
