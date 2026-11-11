package com.opencrm.configuration.session;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Flyway upgrade and Hibernate persistence; this class requires Docker. */
@Tag("integration")
@Testcontainers
class ConfigurationDefaultProvenanceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024).withMemorySwap(256L * 1024 * 1024).withNanoCPUs(1_000_000_000L));
    private EntityManagerFactory emf;

    @BeforeEach
    void upgradeExistingSelections() throws Exception {
        migrations(null).clean();
        migrations("1.4").migrate();
        try (var connection = DriverManager.getConnection(url(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO configuration_sessions(id,uuid,product_id,user_id) VALUES(1,'legacy-session','10','seller')");
            statement.executeUpdate("INSERT INTO configuration_selections(session_id,option_key,option_value) VALUES(1,'manual','30')");
        }
        migrations(null).migrate();
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        factory.setPackagesToScan("com.opencrm.configuration.session");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.default_schema", "configuration"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
    }

    @AfterEach
    void closeFactory() {
        if (emf != null) emf.close();
    }

    @Test
    void existingChoicesRemainManualAndAppliedMarkersSurviveReloadAndExplicitOverride() {
        try (var entityManager = emf.createEntityManager()) {
            entityManager.getTransaction().begin();
            var session = entityManager.find(ConfigurationSession.class, 1L);
            assertThat(session.getSelections()).containsEntry("manual", "30");
            assertThat(session.getDefaultedSelectionKeys()).isEmpty();
            session.getSelections().put("option:1:11", "20");
            session.getDefaultedSelectionKeys().add("option:1:11");
            entityManager.getTransaction().commit();
        }
        try (var entityManager = emf.createEntityManager()) {
            entityManager.getTransaction().begin();
            var loaded = entityManager.find(ConfigurationSession.class, 1L);
            assertThat(loaded.getDefaultedSelectionKeys()).containsExactly("option:1:11");
            loaded.addSelection("option:1:11", "40");
            entityManager.getTransaction().commit();
        }
        try (var entityManager = emf.createEntityManager()) {
            var loaded = entityManager.find(ConfigurationSession.class, 1L);
            assertThat(loaded.getSelections()).containsEntry("manual", "30").containsEntry("option:1:11", "40");
            assertThat(loaded.getDefaultedSelectionKeys()).isEmpty();
        }
    }

    @Test
    void rollbackAfterFlushLeavesBothSelectionsAndProvenanceUntouched() {
        try (var entityManager = emf.createEntityManager()) {
            entityManager.getTransaction().begin();
            var session = entityManager.find(ConfigurationSession.class, 1L);
            session.getSelections().put("option:1:11", "20");
            session.getDefaultedSelectionKeys().add("option:1:11");
            entityManager.flush();
            entityManager.getTransaction().rollback();
        }
        try (var entityManager = emf.createEntityManager()) {
            var loaded = entityManager.find(ConfigurationSession.class, 1L);
            assertThat(loaded.getSelections()).containsExactlyEntriesOf(Map.of("manual", "30"));
            assertThat(loaded.getDefaultedSelectionKeys()).isEmpty();
        }
    }

    private Flyway migrations(String target) {
        var config = Flyway.configure().dataSource(url(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("configuration").defaultSchema("configuration").locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) config.target(target);
        return config.load();
    }

    private static String url() {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=configuration";
    }
}
