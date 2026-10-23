package com.opencrm.quote.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.ramp.QuoteRampStep;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V1_13 adds nullable {@code quotes.tenant_id} with no backfill. Rollback is the manual
 * statement in the migration header. {@code updatable = false} is checked by reloading the row.
 */
@Tag("integration")
@Testcontainers
class QuoteTenantMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024).withMemorySwap(256L * 1024 * 1024));

    @Test
    void columnIsNullableExistingRowsStayNullAndRollbackDropsIt() throws Exception {
        migrations("1.12").migrate();
        long legacyId = insert(null);

        migrations(null).migrate();

        assertThat(column("is_nullable")).isEqualTo("YES");
        assertThat(column("column_default")).isNull();
        assertThat(column("character_maximum_length")).isEqualTo("128");
        assertThat(column("data_type")).isEqualTo("character varying");
        assertThat(tenant(legacyId)).isNull();

        long omitted = insert(null);
        assertThat(tenant(omitted)).isNull();
        long stored = insert("tenant-a");
        assertThat(tenant(stored)).isEqualTo("tenant-a");
        assertThatThrownBy(() -> insert("x".repeat(129)))
                .isInstanceOf(SQLException.class);

        renameWithoutPersistingTenant(stored, "Renamed", "hijacked");
        assertThat(tenant(stored)).isEqualTo("tenant-a");
        assertThat(name(stored)).isEqualTo("Renamed");

        rollback();
        assertThat(column("is_nullable")).isNull();
        assertThat(name(legacyId)).isNotBlank();
        assertThat(name(stored)).isEqualTo("Renamed");
    }

    private static void renameWithoutPersistingTenant(long id, String name, String tenant) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .applySetting("hibernate.connection.username", POSTGRES.getUsername())
                .applySetting("hibernate.connection.password", POSTGRES.getPassword())
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.default_schema", "quote")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try (SessionFactory sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Quote.class)
                .addAnnotatedClass(QuoteLineItem.class)
                .addAnnotatedClass(QuoteRampStep.class)
                .buildMetadata()
                .buildSessionFactory();
                Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            Quote quote = session.find(Quote.class, id);
            quote.setName(name);
            try {
                Quote.class.getMethod("setTenantId", String.class).invoke(quote, tenant);
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError("Quote.tenantId is missing", ex);
            }
            session.getTransaction().commit();
        } catch (RuntimeException ex) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw ex;
        }
    }

    private static Flyway migrations(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("quote")
                .defaultSchema("quote")
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private static long insert(String tenant) throws SQLException {
        String sql = tenant == null
                ? "INSERT INTO quote.quotes(uuid, quote_number, name, customer_external_id) VALUES (?,?,?,?) RETURNING id"
                : "INSERT INTO quote.quotes(uuid, quote_number, name, customer_external_id, tenant_id) VALUES (?,?,?,?,?) RETURNING id";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            String id = UUID.randomUUID().toString();
            statement.setString(1, id);
            statement.setString(2, "Q-" + id);
            statement.setString(3, "Synthetic tenant");
            statement.setString(4, "synthetic-customer");
            if (tenant != null) {
                statement.setString(5, tenant);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static String tenant(long id) throws SQLException {
        return scalar(id, "tenant_id");
    }

    private static String name(long id) throws SQLException {
        return scalar(id, "name");
    }

    private static String scalar(long id, String column) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + column + " FROM quote.quotes WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static String column(String field) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + field + " FROM information_schema.columns "
                                + "WHERE table_schema = 'quote' AND table_name = 'quotes' AND column_name = 'tenant_id'")) {
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return rows.getString(1);
            }
        }
    }

    /** Statements in the V1_13 header after "Apply in the quote schema:". */
    private static void rollback() throws Exception {
        String sql = new String(QuoteTenantMigrationIntegrationTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V1_13__quote_tenant_id.sql")
                .readAllBytes());
        int marker = sql.indexOf("Apply in the quote schema:");
        assertThat(marker).isGreaterThan(0);
        List<String> statements = new ArrayList<>();
        for (String raw : sql.substring(marker).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("--")) {
                break;
            }
            String body = line.substring(2).strip();
            if (body.endsWith(";")) {
                statements.add(body);
            }
        }
        assertThat(statements).containsExactly("ALTER TABLE quotes DROP COLUMN IF EXISTS tenant_id;");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO quote");
            for (String command : statements) {
                statement.execute(command);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
