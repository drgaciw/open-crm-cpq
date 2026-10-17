package com.opencrm.pricing.rule;

import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real migration chain and compiles database seed content, rather than the separate resource corpus. */
@Tag("integration")
@Testcontainers
class ManagedSeedRuleImportsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));
    private JdbcTemplate jdbc;
    private Flyway latest;
    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @BeforeEach
    void seedAppliedV2AndV3ExactlyAsExistingDatabases() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        latest = Flyway.configure().dataSource(dataSource).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").target("7").cleanDisabled(false).load();
        latest.clean();
        Flyway.configure().dataSource(dataSource).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").target("6").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void repairsAllElevenManagedBodiesWithoutChangingRuleLogicOrPolicyMetadata() {
        List<Map<String, Object>> before = seededRows();
        assertThat(before).hasSize(11);
        var originalResult = compiler.validateRules(definitions(before));
        assertThat(originalResult.isValid()).isFalse();
        assertThat(originalResult.joinedErrors()).contains("Unable to resolve ObjectType");

        latest.migrate();

        List<Map<String, Object>> after = seededRows();
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).get("content")).isEqualTo(header() + before.get(i).get("content"));
            var originalMetadata = new HashMap<>(before.get(i));
            var repairedMetadata = new HashMap<>(after.get(i));
            originalMetadata.remove("content"); repairedMetadata.remove("content");
            assertThat(repairedMetadata).isEqualTo(originalMetadata);
        }
        assertThat(compiler.validateRules(definitions(after)).isValid()).isTrue();
    }

    @Test
    void leavesOwnerEditedSeedContentAndMetadataUntouched() {
        jdbc.update("update pricing.rules set content = content || E'\\n// owner-authored revision', updated_by = 'owner' where uuid = 'rule-003'");
        Map<String, Object> edited = jdbc.queryForMap("select * from pricing.rules where uuid = 'rule-003'");

        latest.migrate();

        assertThat(jdbc.queryForMap("select * from pricing.rules where uuid = 'rule-003'")).isEqualTo(edited);
        assertThat(jdbc.queryForObject("select count(*) from pricing.rules where content like 'package com.opencrm.pricing.rules;%'", Long.class))
                .isEqualTo(10);
    }

    private List<Map<String, Object>> seededRows() {
        return jdbc.queryForList("select * from pricing.rules order by uuid");
    }

    private List<RuleDefinition> definitions(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> RuleDefinition.builder().id(row.get("uuid").toString())
                .name(row.get("name").toString()).content(row.get("content").toString()).build()).toList();
    }

    private String header() {
        return "package com.opencrm.pricing.rules;\nimport com.opencrm.rules.model.QuoteFact;\n"
                + "import com.opencrm.rules.model.QuoteLineItemFact;\nimport com.opencrm.rules.model.CustomerFact;\n";
    }
}
