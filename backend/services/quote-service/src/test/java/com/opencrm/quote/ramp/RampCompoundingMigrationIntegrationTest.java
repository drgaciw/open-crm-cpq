package com.opencrm.quote.ramp;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Applies the real upgrade to a legacy row rather than generating the schema from JPA. */
@Tag("integration")
@Testcontainers
class RampCompoundingMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    @Test void upgradePreservesLegacyNullAndPersistsExplicitNonCompounding() throws Exception {
        migrations("1.15").migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var insertQuote = connection.prepareStatement("""
                     insert into quote.quotes (uuid, quote_number, name, customer_external_id)
                     values (?, 'RAMP-LEGACY', 'Legacy ramp', 'test-customer') returning id
                     """);
             var insertLine = connection.prepareStatement("""
                     insert into quote.quote_line_items
                       (uuid, quote_id, product_external_id, list_price, ramp_escalator_start_price,
                        ramp_escalator_mode, ramp_escalator_amount, ramp_escalator_every_months)
                     values (?, ?, 'test-seats', 100, 100, 'PERCENT', 10, 12) returning id
                     """);
             var insertStep = connection.prepareStatement("""
                     insert into quote.quote_ramp_steps
                       (uuid, line_item_id, sequence, start_month, months, quantity, price_basis, unit_price)
                     values (?, ?, 1, 1, 12, 1, 'ABSOLUTE', 100)
                     """)) {
            insertQuote.setString(1, UUID.randomUUID().toString());
            try (var row = insertQuote.executeQuery()) {
                assertThat(row.next()).isTrue();
                insertLine.setLong(2, row.getLong(1));
            }
            insertLine.setString(1, UUID.randomUUID().toString());
            try (var row = insertLine.executeQuery()) {
                assertThat(row.next()).isTrue();
                insertStep.setLong(2, row.getLong(1));
            }
            insertStep.setString(1, UUID.randomUUID().toString());
            assertThat(insertStep.executeUpdate()).isEqualTo(1);
        }

        migrations(null).migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("select ramp_escalator_compounding from quote.quote_line_items")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject(1)).isNull();
            }
            assertThat(statement.executeUpdate("update quote.quote_line_items set ramp_escalator_compounding = false"))
                    .isEqualTo(1);
            try (var row = statement.executeQuery("select ramp_escalator_compounding from quote.quote_line_items")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBoolean(1)).isFalse();
                assertThat(row.wasNull()).isFalse();
            }
        }
        assertThat(migrations(null).info().current().getVersion()).isEqualTo(MigrationVersion.fromVersion("1.16"));

        String rollback;
        try (var migration = getClass().getResourceAsStream("/db/migration/V1_16__ramp_escalator_compounding.sql")) {
            assertThat(migration).isNotNull();
            rollback = new String(migration.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("-- Rollback:"))
                    .findFirst().orElseThrow().substring("-- Rollback:".length()).trim();
        }
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute(rollback);
            try (var row = statement.executeQuery("""
                    select count(*) from information_schema.columns
                    where table_schema = 'quote' and table_name = 'quote_line_items'
                      and column_name = 'ramp_escalator_compounding'
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isZero();
            }
            try (var row = statement.executeQuery("""
                    select q.quote_number, q.duration_in_months, l.product_external_id, l.list_price,
                           l.ramp_escalator_mode, l.ramp_escalator_amount,
                           s.start_month, s.months, s.quantity, s.price_basis, s.unit_price
                    from quote.quotes q
                    join quote.quote_line_items l on l.quote_id = q.id
                    join quote.quote_ramp_steps s on s.line_item_id = l.id
                    where q.quote_number = 'RAMP-LEGACY'
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("quote_number")).isEqualTo("RAMP-LEGACY");
                assertThat(row.getInt("duration_in_months")).isEqualTo(12);
                assertThat(row.getString("product_external_id")).isEqualTo("test-seats");
                assertThat(row.getBigDecimal("list_price")).isEqualByComparingTo("100");
                assertThat(row.getString("ramp_escalator_mode")).isEqualTo("PERCENT");
                assertThat(row.getBigDecimal("ramp_escalator_amount")).isEqualByComparingTo("10");
                assertThat(row.getInt("start_month")).isEqualTo(1);
                assertThat(row.getInt("months")).isEqualTo(12);
                assertThat(row.getInt("quantity")).isEqualTo(1);
                assertThat(row.getString("price_basis")).isEqualTo("ABSOLUTE");
                assertThat(row.getBigDecimal("unit_price")).isEqualByComparingTo("100");
                assertThat(row.next()).isFalse();
            }
        }
    }

    private static Flyway migrations(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("quote").defaultSchema("quote").locations("classpath:db/migration");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }
}
