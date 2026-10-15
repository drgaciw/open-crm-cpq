package com.opencrm.pricing.cost;

import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.security.CurrentActor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD (2): replaying an export is a no-op, on PostgreSQL with the real migration chain (V1..V9) and Hibernate
 * {@code ddl-auto=validate}, so the entities are also checked against {@code V9__cost_basis.sql}. Needs Docker; the
 * {@code unit-tests} profile excludes it.
 */
@Tag("integration")
@Testcontainers
@DisplayName("cost basis on PostgreSQL: replay is a no-op, a higher version replaces, every import is audited")
class CostBasisReplayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    private JdbcTemplate jdbc;
    private ApplicationContextRunner context;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = {CostBasisRow.class, PricingProduct.class})
    @EnableJpaRepositories(basePackageClasses = {CostBasisRowRepository.class, PricingProductRepository.class})
    @EnableConfigurationProperties(CostBasisProperties.class)
    @Import({CostBasisIngestionService.class, JpaCostBasisLookup.class})
    static class Persistence {
        @Bean
        CurrentActor currentActor() {
            return new CurrentActor("service:pricing-service-it", true);
        }
    }

    @BeforeEach
    void migrateAndSeedCatalog() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        CostExportCsv.read(CostExportCsvTest.fixtureV1(), 10_000).rows().stream()
                .map(CostExportRow::productExternalId).distinct()
                .forEach(id -> jdbc.update("insert into pricing.pricing_products (uuid, external_id, name, product_type)"
                        + " values (?, ?, ?, 'SAAS_SEAT_NAMED')", UUID.randomUUID().toString(), id, id));

        context = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                        org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
                        org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration.class))
                .withUserConfiguration(Persistence.class)
                .withPropertyValues(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl()
                                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=pricing",
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.default_schema=pricing");
    }

    @Test
    @DisplayName("ingesting mock v1 twice stores 31 rows once, audits two imports, and the lookup is unchanged")
    void replayIsNoOp() {
        context.run(application -> {
            CostBasisIngestionService ingestion = application.getBean(CostBasisIngestionService.class);
            CostBasisLookup lookup = application.getBean(CostBasisLookup.class);
            String v1 = CostExportCsvTest.fixtureV1();

            CostBasisImportSummary first = ingestion.ingest(v1).audit();
            UnitCost before = lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 1)).orElseThrow();
            CostBasisImportSummary replay = ingestion.ingest(v1).audit();
            UnitCost after = lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 1)).orElseThrow();

            assertThat(first.inserted()).isEqualTo(31);
            assertThat(replay.inserted()).isZero();
            assertThat(replay.unchanged()).isEqualTo(31);
            assertThat(count("cost_basis_rows")).isEqualTo(31);
            assertThat(after).isEqualTo(before);
            assertThat(after.unitCost()).isEqualByComparingTo("10.48");

            assertThat(count("cost_basis_imports")).isEqualTo(2);
            Map<String, Object> audit = jdbc.queryForMap(
                    "select * from pricing.cost_basis_imports where id = ?", replay.importId());
            assertThat(audit).containsEntry("export_version", 1L)
                    .containsEntry("approved_by", "Danny (project owner, Finance approver)")
                    .containsEntry("ingested_by", "service:pricing-service-it")
                    .containsEntry("rows_accepted", 31)
                    .containsEntry("rows_rejected", 0);
        });
    }

    @Test
    @DisplayName("a higher version replaces one key; v1 stays on record; stale from 2027-04-01")
    void higherVersionReplacesAndStalenessApplies() {
        context.run(application -> {
            CostBasisIngestionService ingestion = application.getBean(CostBasisIngestionService.class);
            CostBasisLookup lookup = application.getBean(CostBasisLookup.class);
            ingestion.ingest(CostExportCsvTest.fixtureV1());

            ingestion.ingest(CostExportCsvTest.HEADER + "\nPROD-SEAT,DIRECT,seat,7.0000,USD,2026-10-01,2,Finance");

            UnitCost cost = lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 1)).orElseThrow();
            assertThat(cost.unitCost()).isEqualByComparingTo("11.40");
            assertThat(cost.version()).isEqualTo(2L);
            assertThat(count("cost_basis_rows")).isEqualTo(32);
            assertThat(lookup.find("PROD-SEAT", LocalDate.of(2027, 3, 31))).isPresent();
            assertThat(lookup.find("PROD-SEAT", LocalDate.of(2027, 4, 1))).isEmpty();
        });
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from pricing." + table, Long.class);
    }
}
