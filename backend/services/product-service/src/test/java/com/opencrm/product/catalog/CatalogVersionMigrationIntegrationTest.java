package com.opencrm.product.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO product.catalog_versions(
                    uuid, version_number, published_at, product_count)
                VALUES (?, ?, ?, 1)
                RETURNING id
                """)) {
            statement.setString(1, versionUuid);
            statement.setInt(2, versionNumber);
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-11-12T00:00:00Z")));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
