package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
class QuoteNumberAllocationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource firstDataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void migrateLegacyThenUpgrade() {
        firstDataSource = isolatedDataSource();
        jdbc = new JdbcTemplate(firstDataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(firstDataSource));

        Flyway.configure().dataSource(firstDataSource).schemas("quote").target("1.20").load().migrate();
        insert(jdbc, "Q-20300102-0008", false);
        insert(jdbc, "Q-20300102-9223372036854775808", true);
        insert(jdbc, "Q-20300102-imported-legacy", false);
        Flyway.configure().dataSource(firstDataSource).schemas("quote").load().migrate();
    }

    @Test
    void startsEachDayAtOneAndPreservesNumericImportedHistory() {
        assertThat(allocate(LocalDate.of(2030, 1, 1))).isEqualTo("Q-20300101-0001");
        var imported = LocalDate.of(2030, 1, 2);
        assertThat(allocate(imported)).isEqualTo("Q-20300102-9223372036854775809");
        insert(jdbc, "Q-20300102-9223372036854775819", true);
        assertThat(allocate(imported)).isEqualTo("Q-20300102-9223372036854775820");
        assertThat(allocate(LocalDate.of(2030, 1, 3))).isEqualTo("Q-20300103-0001");
    }

    @Test
    void rollbackReleasesTheAllocationWithTheQuote() {
        LocalDate day = LocalDate.of(2030, 1, 4);
        String aborted = transactions.execute(status -> {
            String number = new QuoteNumberGenerator(jdbc).generate(day);
            insert(jdbc, number, false);
            status.setRollbackOnly();
            return number;
        });

        assertThat(allocate(day)).isEqualTo(aborted).isEqualTo("Q-20300104-0001");
        assertThat(allocate(day)).isEqualTo("Q-20300104-0002");
    }

    @Test
    void errorsClearlyWhenNoLargerNumberFitsTheColumn() {
        LocalDate day = LocalDate.of(2030, 1, 5);
        insert(jdbc, "Q-20300105-" + "9".repeat(39), true);

        assertThatThrownBy(() -> allocate(day)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "QUOTE_NUMBER_EXHAUSTED");
        assertThat(jdbc.queryForObject(
                "select count(*) from quote.quote_number_counters where quote_day = ?",
                Long.class, day)).isZero();
    }

    @Test
    void independentSessionsAllocateDistinctNumbersWhileFirstQuoteIsUncommitted() throws Exception {
        LocalDate day = LocalDate.of(2030, 1, 6);
        var secondDataSource = isolatedDataSource();
        var secondJdbc = new JdbcTemplate(secondDataSource);
        var secondTransactions = new TransactionTemplate(new DataSourceTransactionManager(secondDataSource));
        var firstInserted = new CountDownLatch(1);
        var finishFirst = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> transactions.execute(status -> {
                String number = new QuoteNumberGenerator(jdbc).generate(day);
                insert(jdbc, number, false);
                firstInserted.countDown();
                await(finishFirst);
                return number;
            }));
            try {
                assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> secondTransactions.execute(status -> {
                    secondJdbc.execute("set local application_name = 'quote-number-allocation-test-second'");
                    String number = new QuoteNumberGenerator(secondJdbc).generate(day);
                    insert(secondJdbc, number, false);
                    return number;
                }));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                boolean waiting;
                do {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            select exists (select 1 from pg_stat_activity
                            where application_name = 'quote-number-allocation-test-second'
                            and wait_event_type = 'Lock')
                            """, Boolean.class));
                    if (!waiting && !second.isDone()) {
                        Thread.sleep(25);
                    }
                } while (!waiting && !second.isDone() && System.nanoTime() < deadline);
                assertThat(waiting).as("second writer waits while the first quote is uncommitted").isTrue();

                finishFirst.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("Q-20300106-0001");
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("Q-20300106-0002");
            } finally {
                finishFirst.countDown();
            }
        }
    }

    private static DriverManagerDataSource isolatedDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String allocate(LocalDate day) {
        return transactions.execute(status -> new QuoteNumberGenerator(jdbc).generate(day));
    }

    private static void insert(JdbcTemplate client, String number, boolean archived) {
        client.update("""
                insert into quote.quotes (uuid, quote_number, name, customer_external_id, is_deleted)
                values (?, ?, 'Quote allocation fixture', 'synthetic', ?)
                """, UUID.randomUUID().toString(), number, archived);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the second quote writer");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
