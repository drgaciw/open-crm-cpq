package com.opencrm.product.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DRG-418 / BC-01-01: SKU uniqueness is per tenant (V1_9), not global. Real migration coverage: an HTTP- or
 * mock-repository-level test cannot exercise the PostgreSQL unique index.
 */
@Tag("integration")
@Testcontainers
class ProductTenantSkuUniquenessIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024).withMemorySwap(256L * 1024 * 1024).withNanoCPUs(1_000_000_000L));

    @BeforeEach
    void freshCatalog() {
        migrations().clean();
        migrations().migrate();
    }

    @Test
    void sameSkuIsRejectedWithinTheSameTenant() throws Exception {
        insert("tenant-a", "SKU-SHARED");
        assertThatThrownBy(() -> insert("tenant-a", "SKU-SHARED"))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23505");
    }

    @Test
    void sameSkuIsAllowedAcrossDifferentTenants() throws Exception {
        insert("tenant-a", "SKU-CROSS-TENANT");
        insert("tenant-b", "SKU-CROSS-TENANT");
        assertThat(countProducts()).isEqualTo(2);
    }

    @Test
    void sameSkuIsAllowedAcrossTwoUnresolvedTenantRows() throws Exception {
        // Both rows have a null tenant_id (service tokens, dev mode, pre-DRG-418 rows). Postgres treats NULL
        // <> NULL in a unique index, so this is accepted — the documented limit of the interim tenant column.
        insert(null, "SKU-NULL-TENANT");
        insert(null, "SKU-NULL-TENANT");
        assertThat(countProducts()).isEqualTo(2);
    }

    private static Flyway migrations() {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("product").defaultSchema("product").locations("classpath:db/migration").cleanDisabled(false)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insert(String tenantId, String sku) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO product.products(uuid,sku,name,product_type,category,status,tenant_id) "
                        + "VALUES(?,?,?,'CUSTOM','SUBSCRIPTION','DRAFT',?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, sku);
            statement.setString(3, "Synthetic tenant-scoped SKU test");
            statement.setString(4, tenantId);
            statement.executeUpdate();
        }
    }

    private static int countProducts() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM product.products")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
