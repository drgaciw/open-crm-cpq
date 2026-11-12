package com.opencrm.product.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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

/**
 * DRG-421: V1_10 catalog_versions / catalog_version_products migrate and hold an immutable snapshot.
 */
@Tag("integration")
@Testcontainers
class CatalogVersionMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024)
                    .withMemorySwap(256L * 1024 * 1024)
                    .withNanoCPUs(1_000_000_000L));

    @BeforeEach
    void cleanSchema() {
        flyway().clean();
    }

    @Test
    void v1_10CreatesCatalogVersionTablesAndStoresImmutableSnapshot() throws Exception {
        flyway().migrate();

        try (Connection connection = connection()) {
            assertThat(tableExists(connection, "catalog_versions")).isTrue();
            assertThat(tableExists(connection, "catalog_version_products")).isTrue();

            long productId = insertProduct(connection, "SKU-PUB", "ACTIVE");
            insertProduct(connection, "SKU-DRAFT", "DRAFT");

            String versionUuid = UUID.randomUUID().toString();
            long versionId = insertCatalogVersion(connection, versionUuid, 1);

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO product.catalog_version_products(
                        catalog_version_id, product_id, product_uuid, sku, name,
                        product_type, category, status, list_price, currency,
                        is_configurable, is_bundle)
                    SELECT ?, id, uuid, sku, name, product_type, category, status, list_price, currency,
                           is_configurable, is_bundle
                    FROM product.products
                    WHERE id = ?
                    """)) {
                statement.setLong(1, versionId);
                statement.setLong(2, productId);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT sku, status FROM product.catalog_version_products WHERE catalog_version_id = ?")) {
                statement.setLong(1, versionId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("sku")).isEqualTo("SKU-PUB");
                    assertThat(rows.getString("status")).isEqualTo("ACTIVE");
                    assertThat(rows.next()).isFalse();
                }
            }

            // Live draft remains in products but is absent from the published snapshot.
            try (ResultSet drafts = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM product.products WHERE status = 'DRAFT'")) {
                drafts.next();
                assertThat(drafts.getInt(1)).isEqualTo(1);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM product.catalog_version_products WHERE catalog_version_id = ? AND sku = 'SKU-DRAFT'")) {
                statement.setLong(1, versionId);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    void tenantScopedLookupDoesNotResolveForeignTenantVersionUuid() throws Exception {
        flyway().migrate();
        try (Connection connection = connection()) {
            String foreignUuid = UUID.randomUUID().toString();
            insertCatalogVersionForTenant(connection, foreignUuid, 1, "tenant-b");

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT count(*) FROM product.catalog_versions
                    WHERE uuid = ? AND NOT is_deleted
                      AND ((?::text IS NULL AND tenant_id IS NULL) OR tenant_id = ?)
                    """)) {
                statement.setString(1, foreignUuid);
                statement.setString(2, "tenant-a");
                statement.setString(3, "tenant-a");
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    void concurrentSameTenantPublishWithJdbcTemplateAdvisoryLockAllocatesDistinctVersionNumbers()
            throws Exception {
        flyway().migrate();
        // Exercises CatalogPublishLock's Spring JDBC path (void lock → rs -> null), not raw JDBC.
        DataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> allocated = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(
                    () -> publishNextVersion(tx, jdbc, "tenant-a", ready, start, allocated));
            Future<?> second = pool.submit(
                    () -> publishNextVersion(tx, jdbc, "tenant-a", ready, start, allocated));
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(allocated).containsExactlyInAnyOrder(1, 2);
        assertThat(jdbc.queryForList(
                        "SELECT version_number FROM product.catalog_versions WHERE tenant_id = ? AND NOT is_deleted "
                                + "ORDER BY version_number",
                        Integer.class,
                        "tenant-a"))
                .containsExactly(1, 2);
    }

    private static void publishNextVersion(
            TransactionTemplate tx,
            JdbcTemplate jdbc,
            String tenantId,
            CountDownLatch ready,
            CountDownLatch start,
            Set<Integer> allocated) {
        ready.countDown();
        try {
            if (!start.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent start");
            }
            Integer next = tx.execute(status -> {
                // Same SQL + RowCallbackHandler as CatalogPublishLock.lock (Spring JDBC, not Spring Data).
                jdbc.query(
                        "SELECT pg_advisory_xact_lock(hashtext(CAST(? AS text)))",
                        rs -> null,
                        tenantId);
                Integer max = jdbc.queryForObject(
                        "SELECT COALESCE(MAX(version_number), 0) FROM product.catalog_versions "
                                + "WHERE NOT is_deleted AND tenant_id = ?",
                        Integer.class,
                        tenantId);
                int versionNumber = (max == null ? 0 : max) + 1;
                jdbc.update(
                        """
                        INSERT INTO product.catalog_versions(
                            uuid, version_number, tenant_id, published_at, product_count)
                        VALUES (?, ?, ?, ?, 1)
                        """,
                        UUID.randomUUID().toString(),
                        versionNumber,
                        tenantId,
                        Timestamp.from(Instant.parse("2026-11-12T00:00:00Z")));
                return versionNumber;
            });
            allocated.add(next);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("product")
                .defaultSchema("product")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'product' AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static long insertProduct(Connection connection, String sku, String status) throws SQLException {
        String uuid = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO product.products(
                    uuid, sku, name, product_type, category, status, list_price, currency)
                VALUES (?, ?, ?, 'CUSTOM', 'SUBSCRIPTION', ?, 10, 'USD')
                RETURNING id
                """)) {
            statement.setString(1, uuid);
            statement.setString(2, sku);
            statement.setString(3, "Product " + sku);
            statement.setString(4, status);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long insertCatalogVersion(Connection connection, String versionUuid, int versionNumber)
            throws SQLException {
        return insertCatalogVersionForTenant(connection, versionUuid, versionNumber, null);
    }

    private static long insertCatalogVersionForTenant(
            Connection connection, String versionUuid, int versionNumber, String tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO product.catalog_versions(
                    uuid, version_number, tenant_id, published_at, product_count)
                VALUES (?, ?, ?, ?, 1)
                RETURNING id
                """)) {
            statement.setString(1, versionUuid);
            statement.setInt(2, versionNumber);
            statement.setString(3, tenantId);
            statement.setTimestamp(4, Timestamp.from(Instant.parse("2026-11-12T00:00:00Z")));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
