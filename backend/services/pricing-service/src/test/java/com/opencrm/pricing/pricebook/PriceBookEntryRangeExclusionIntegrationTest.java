package com.opencrm.pricing.pricebook;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DRG-431: V11 gist exclusion must reject concurrent overlapping inserts that the Java check can miss.
 */
@Tag("integration")
@Testcontainers
class PriceBookEntryRangeExclusionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").cleanDisabled(false).load()
                .clean();
        Flyway.configure().dataSource(dataSource).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource));
    }

    @Test
    void concurrentOverlappingInsertsLeaveExactlyOneActivePriceBookEntry() throws Exception {
        long bookId = insertPriceBook();
        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Runnable writer = () -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                insertOverlappingOrConflict(bookId, successes, failures);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            } finally {
                done.countDown();
            }
        };

        Thread first = new Thread(writer, "price-entry-writer-1");
        Thread second = new Thread(writer, "price-entry-writer-2");
        first.start();
        second.start();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        first.join(5_000);
        second.join(5_000);

        assertThat(successes.get() + failures.get()).isEqualTo(2);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(activePriceEntries(bookId)).isEqualTo(1);
    }

    private void insertOverlappingOrConflict(long bookId, AtomicInteger successes, AtomicInteger failures) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                insertPriceEntry(bookId, "2026-01-01", "2026-06-30");
                successes.incrementAndGet();
                return;
            } catch (Exception ex) {
                String detail = mostSpecific(ex);
                if (detail.contains("price_book_entries_no_overlapping_range")) {
                    failures.incrementAndGet();
                    return;
                }
                if (detail.contains("deadlock detected") && attempt < 2) {
                    continue;
                }
                if (detail.contains("deadlock detected")) {
                    failures.incrementAndGet();
                    return;
                }
                throw new IllegalStateException(ex);
            }
        }
    }

    @Test
    void adjacentRangesAndSoftDeletedHistoryAreAllowed() {
        long bookId = insertPriceBook();
        insertPriceEntry(bookId, "2026-01-01", "2026-06-30");
        insertPriceEntry(bookId, "2026-07-01", "2026-12-31");
        jdbc.update("UPDATE pricing.price_book_entries SET is_deleted = true WHERE price_book_id = ?", bookId);
        insertPriceEntry(bookId, "2026-01-01", "2026-06-30");
        assertThat(activePriceEntries(bookId)).isEqualTo(1);
    }

    @Test
    void overlappingCostBookInsertIsRejected() {
        long bookId = jdbc.queryForObject(
                """
                INSERT INTO pricing.cost_books (uuid, name, status, version_number)
                VALUES (?, 'Costs', 'DRAFT', 1)
                RETURNING id
                """,
                Long.class,
                UUID.randomUUID().toString());
        jdbc.update(
                """
                INSERT INTO pricing.cost_book_entries
                    (uuid, cost_book_id, sku, cost, currency, unit_of_measure,
                     effective_start_date, effective_end_date)
                VALUES (?, ?, 'SKU-1', 10, 'USD', 'EACH', DATE '2026-01-01', DATE '2026-06-30')
                """,
                UUID.randomUUID().toString(), bookId);
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO pricing.cost_book_entries
                    (uuid, cost_book_id, sku, cost, currency, unit_of_measure,
                     effective_start_date, effective_end_date)
                VALUES (?, ?, 'SKU-1', 12, 'USD', 'EACH', DATE '2026-04-01', DATE '2026-12-31')
                """,
                UUID.randomUUID().toString(), bookId))
                .satisfies(ex -> assertThat(mostSpecific(ex)).contains("cost_book_entries_no_overlapping_range"));
    }

    private long insertPriceBook() {
        return jdbc.queryForObject(
                """
                INSERT INTO pricing.price_books (uuid, name, status, version_number)
                VALUES (?, 'Race', 'DRAFT', 1)
                RETURNING id
                """,
                Long.class,
                UUID.randomUUID().toString());
    }

    private void insertPriceEntry(long bookId, String start, String end) {
        jdbc.update(
                """
                INSERT INTO pricing.price_book_entries
                    (uuid, price_book_id, product_external_id, list_price, currency, unit_of_measure,
                     effective_start_date, effective_end_date)
                VALUES (?, ?, 'SKU-1', 100, 'USD', 'EACH', DATE '%s', DATE '%s')
                """.formatted(start, end),
                UUID.randomUUID().toString(), bookId);
    }

    private int activePriceEntries(long bookId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pricing.price_book_entries WHERE price_book_id = ? AND is_deleted = false",
                Integer.class,
                bookId);
        return count == null ? 0 : count;
    }

    private static String mostSpecific(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? "" : message;
    }
}
