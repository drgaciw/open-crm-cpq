package com.opencrm.quote.comparison;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL Flyway upgrade, first-write race, independent scopes and rollback; Docker required. */
@Tag("integration")
@Testcontainers
class QuoteComparisonPrimaryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private QuotePrimarySelectionRepository selections;

    @BeforeEach void setup() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS quote CASCADE");
        migrate("1.16");
        insert(1, "workforce", "deal");
        insert(2, "workforce", "deal");
        migrate(null);
        selections = new QuotePrimarySelectionRepository(jdbc);
    }

    @AfterEach void cleanup() { jdbc.execute("DROP SCHEMA IF EXISTS quote CASCADE"); }

    @Test void upgradePreservesExistingQuotesAndPreferenceReloadsWithActor() {
        assertThat(jdbc.queryForObject("select count(*) from quote.quotes", Long.class)).isEqualTo(2L);
        selections.select("workforce", "deal", 1L, "seller-1");
        assertThat(new QuotePrimarySelectionRepository(jdbc).selectedQuote("workforce", "deal")).contains(1L);
        selections.select("workforce", "deal", 2L, "seller-2");
        assertThat(selections.selectedQuote("workforce", "deal")).contains(2L);
        assertThat(jdbc.queryForObject("select selected_by from quote.quote_primary_selections", String.class))
                .isEqualTo("seller-2");
        assertThat(jdbc.queryForObject("select status from quote.quotes where id=2", String.class)).isEqualTo("DRAFT");
    }

    @Test void concurrentFirstSelectionsHaveExactlyOneDurableWinner() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(1L, 2L).stream().map(id -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race start timed out");
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status ->
                        selections.select("workforce", "deal", id, "seller-" + id));
                return id;
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from quote.quote_primary_selections", Long.class)).isEqualTo(1L);
        assertThat(selections.selectedQuote("workforce", "deal").orElseThrow()).isIn(1L, 2L);
    }

    @Test void preferenceRollbackRetainsPreviousSelectionAndIndependentScope() {
        insert(3, "another-workforce", "deal");
        selections.select("workforce", "deal", 1L, "seller");
        selections.select("another-workforce", "deal", 3L, "other-seller");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            selections.select("workforce", "deal", 2L, "seller");
            throw new IllegalStateException("failure after upsert");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(selections.selectedQuote("workforce", "deal")).contains(1L);
        assertThat(selections.selectedQuote("another-workforce", "deal")).contains(3L);
    }

    @Test void freshSchemaAppliesAllMigrationsAndForeignKeyRefusesMissingQuote() {
        jdbc.execute("DROP SCHEMA quote CASCADE");
        migrate(null);
        assertThat(jdbc.queryForObject("select count(*) from quote.quote_primary_selections", Long.class)).isZero();
        assertThatThrownBy(() -> selections.select("workforce", "deal", 999L, "seller"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void migrate(String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas("quote").defaultSchema("quote")
                .locations("classpath:db/migration");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }

    private void insert(long id, String tenant, String deal) {
        jdbc.update("insert into quote.quotes (id, uuid, quote_number, name, customer_external_id, tenant_id, deal_external_id) "
                + "values (?, ?, ?, 'Alternative', 'account', ?, ?)",
                id, UUID.randomUUID().toString(), "Q-" + id, tenant, deal);
    }
}
