package com.opencrm.product.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real migration coverage: HTTP enum conversion alone does not exercise PostgreSQL CHECK constraints. */
@Tag("integration")
@Testcontainers
class ProductTypeConstraintIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024).withMemorySwap(256L * 1024 * 1024).withNanoCPUs(1_000_000_000L));

    private static final List<String> CANONICAL = List.of(
            "SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
    // The shipped V1 domain must remain valid when upgrading an existing catalog.
    private static final List<String> LEGACY = List.of(
            "SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "SAAS_USAGE_METERED", "SAAS_FLAT_RATE",
            "ONPREM_PERPETUAL", "ONPREM_CORE", "ONPREM_SUBSCRIPTION", "HYBRID_PORTABLE",
            "SUPPORT_BASIC", "SUPPORT_PREMIUM", "SUPPORT_ENTERPRISE", "PROFESSIONAL_SERVICES", "TRAINING",
            "ADD_ON_API", "ADD_ON_STORAGE", "ADD_ON_ANALYTICS", "ADD_ON_SECURITY", "ADD_ON_INTEGRATION",
            "HARDWARE", "CUSTOM");

    @BeforeEach
    void emptyOwnedCatalog() {
        migrations(null).clean();
    }

    @Test
    void freshCatalogPersistsAllFourCanonicalRecurringTypes() throws Exception {
        migrations(null).migrate();
        for (String type : CANONICAL) insert(type, "SUBSCRIPTION", "DRAFT", "MONTHLY");
        assertThat(countProducts()).isEqualTo(4);
    }

    @Test
    void upgradePreservesEveryLegacyTypeAndAllowsCanonicalRampTypes() throws Exception {
        migrations("1.7").migrate();
        for (String type : LEGACY) insert(type, "SUBSCRIPTION", "DRAFT", "MONTHLY");
        assertThat(countProducts()).isEqualTo(LEGACY.size());
        migrations(null).migrate();
        for (String type : CANONICAL) insert(type, "SUBSCRIPTION", "DRAFT", "MONTHLY");
        assertThat(countProducts()).isEqualTo(LEGACY.size() + CANONICAL.size());
        try (Connection connection = connection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT DISTINCT product_type FROM product.products")) {
            var persisted = new java.util.HashSet<String>();
            while (rows.next()) persisted.add(rows.getString(1));
            assertThat(persisted).containsAll(LEGACY).containsAll(CANONICAL);
        }
    }

    @Test
    void migratedCatalogStillRejectsUnknownTypeAndInvalidOtherDomains() {
        migrations(null).migrate();
        assertCheckViolation("UNKNOWN_PRODUCT", "SUBSCRIPTION", "DRAFT", "MONTHLY");
        assertCheckViolation("SAAS_SEAT_NAMED", "UNKNOWN_CATEGORY", "DRAFT", "MONTHLY");
        assertCheckViolation("SAAS_SEAT_NAMED", "SUBSCRIPTION", "UNKNOWN_STATUS", "MONTHLY");
        assertCheckViolation("SAAS_SEAT_NAMED", "SUBSCRIPTION", "DRAFT", "UNKNOWN_FREQUENCY");
    }

    private static Flyway migrations(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("product").defaultSchema("product").locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insert(String type, String category, String status, String frequency) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO product.products(uuid,sku,name,product_type,category,status,list_price,currency,billing_frequency) VALUES(?,?,?,?,?,?,100,'USD',?)")) {
            String id = UUID.randomUUID().toString();
            statement.setString(1, id);
            statement.setString(2, "constraint-test-" + id);
            statement.setString(3, "Synthetic migration constraint test");
            statement.setString(4, type);
            statement.setString(5, category);
            statement.setString(6, status);
            statement.setString(7, frequency);
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

    private static void assertCheckViolation(String type, String category, String status, String frequency) {
        assertThatThrownBy(() -> insert(type, category, status, frequency))
                .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }
}
