package com.opencrm.pricebook;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate {@code create-drop} with {@code default_schema=pricing} issues {@code drop schema pricing}
 * before it creates the schema. On a fresh database that statement is
 * {@code PSQLException: schema "pricing" does not exist}.
 */
class PriceBookPricingSchemaBootstrapTest {

    @Test
    void pricingSchemaExistsBeforeHibernateCreateDrop() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/init-test.sql")) {
            assertThat(in).as("db/init-test.sql").isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("CREATE SCHEMA IF NOT EXISTS pricing");
        }
    }

    @Test
    void integrationContainersRunThePricingSchemaScript() throws Exception {
        assertThat(initScripts(PriceBookOutboxWiringTest.class)).containsExactly("db/init-test.sql");
        assertThat(initScripts(PriceBookEntryRepositoryIntegrationTest.class)).containsExactly("db/init-test.sql");
    }

    private static List<String> initScripts(Class<?> testClass) throws ReflectiveOperationException {
        Field postgres = testClass.getDeclaredField("POSTGRES");
        postgres.setAccessible(true);
        Field paths = JdbcDatabaseContainer.class.getDeclaredField("initScriptPaths");
        paths.setAccessible(true);
        Object scripts = paths.get(postgres.get(null));
        if (!(scripts instanceof List<?> list)) {
            throw new AssertionError("initScriptPaths was " + scripts);
        }
        List<String> copy = new ArrayList<>();
        for (Object script : list) {
            copy.add(String.valueOf(script));
        }
        return copy;
    }
}
