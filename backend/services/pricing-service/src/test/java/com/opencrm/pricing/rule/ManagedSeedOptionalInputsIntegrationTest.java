package com.opencrm.pricing.rule;

import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.model.CustomerFact;
import com.opencrm.rules.model.QuoteFact;
import com.opencrm.rules.model.QuoteLineItemFact;
import java.math.BigDecimal;
import java.util.List;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Executes all database-managed rules with absent optional inputs and real policy inputs. */
@Tag("integration")
@Testcontainers
class ManagedSeedOptionalInputsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));
    private JdbcTemplate jdbc;
    private Flyway latest;
    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @BeforeEach
    void seedExistingDatabaseThroughAppliedV7() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        latest = Flyway.configure().dataSource(source).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").cleanDisabled(false).load();
        latest.clean();
        Flyway.configure().dataSource(source).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").target("7").load().migrate();
        jdbc = new JdbcTemplate(source);
    }

    @Test
    void allElevenRulesExecuteMinimalNonUsageRampFactsAfterVerifiedRedAtV7() {
        QuoteFact original = quote();
        assertThatThrownBy(() -> fire(original, namedLine(original, 50)))
                .hasStackTraceContaining("NullPointerException")
                .hasStackTraceContaining("Route_Approval_for_Margin_Exception");
        latest.migrate();
        QuoteFact quote = quote();
        QuoteLineItemFact line = namedLine(quote, 50);
        fire(quote, line);
        assertThat(line.getFinalPrice()).isEqualByComparingTo("50");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo("77400");
        assertThat(quote.getDecimal("overageAmount")).isNull();
        assertThat(quote.getString("approvalTier")).isNull();
        assertThat(quote.getDecimal("migrationCredit")).isNull();
        // Optional product/deal branches also tolerate incomplete metadata.
        quote = quote(); quote.setString("dealType", "AMENDMENT");
        QuoteLineItemFact core = QuoteLineItemFact.builder().quote(quote).lineItemType("ONPREM_CORE").build();
        fire(quote, core);
        assertThat(core.getInt("billableCores")).isNull();
        assertThat(quote.getDecimal("prorationAdjustment")).isNull();
        quote.setInt("remainingTermDays", 90); quote.setInt("fullTermDays", 0);
        quote.setDecimal("amendmentDeltaAmount", new BigDecimal("1200"));
        fire(quote, core);
        assertThat(quote.getDecimal("prorationAdjustment")).isNull();
    }

    @Test
    void retainsValidPolicyOutputsAndDoesNotHideMalformedNumericInput() {
        latest.migrate();
        QuoteFact quote = quote(); quote.setFinalPrice(new BigDecimal("100000"));
        quote.setDecimal("acv", new BigDecimal("100000"));
        quote.setCustomer(CustomerFact.builder().churnScore(80).build());
        quote.setInt("includedUnits", 10); quote.setInt("actualUnits", 20);
        quote.setDecimal("overageRate", new BigDecimal("2"));
        quote.setBoolean("hasPerpetualEntitlement", true); quote.setBoolean("migrationProgramActive", true);
        quote.setDecimal("perpetualBookValue", new BigDecimal("1000"));
        quote.setDecimal("estimatedMarginPct", new BigDecimal("0.34"));
        quote.setString("dealType", "AMENDMENT"); quote.setInt("remainingTermDays", 90);
        quote.setInt("fullTermDays", 360); quote.setDecimal("amendmentDeltaAmount", new BigDecimal("1200"));
        QuoteLineItemFact named = namedLine(quote, 100);
        QuoteLineItemFact core = QuoteLineItemFact.builder().quote(quote).lineItemType("ONPREM_CORE").build();
        core.setInt("coreCount", 17);
        QuoteLineItemFact perpetual = QuoteLineItemFact.builder().quote(quote).lineItemType("ONPREM_PERPETUAL")
                .listPrice(new BigDecimal("1000")).build();
        fire(quote, named, core, perpetual);
        assertThat(quote.getDecimal("overageAmount")).isEqualByComparingTo("20");
        assertThat(core.getInt("billableCores")).isEqualTo(32);
        assertThat(perpetual.getDecimal("maintenanceAmount")).isEqualByComparingTo("220");
        assertThat(named.getDiscountRate()).isEqualByComparingTo("0.07");
        assertThat(quote.getDecimal("migrationCredit")).isEqualByComparingTo("300");
        assertThat(quote.getString("approvalTier")).isEqualTo("DEAL_DESK");
        assertThat(quote.getDecimal("prorationAdjustment")).isEqualByComparingTo("300");
        assertThat(quote.getDecimal("churnAdjustment")).isEqualByComparingTo("-0.05");
        QuoteFact renewal = quote(); renewal.setString("dealType", "RENEWAL");
        renewal.setCustomer(CustomerFact.builder().churnScore(80).build()); fire(renewal);
        assertThat(renewal.getDecimal("renewalUpliftPct")).isEqualByComparingTo("0.03");
        QuoteFact tcv = quote(); tcv.setFinalPrice(null); tcv.setBasePrice(new BigDecimal("500000"));
        tcv.setDecimal("tcv", new BigDecimal("500000")); tcv.setDecimal("acv", new BigDecimal("500000"));
        fire(tcv); assertThat(tcv.getFinalPrice()).isEqualByComparingTo("550000");
        QuoteFact floor = quote(); floor.setFinalPrice(new BigDecimal("20000"));
        fire(floor); assertThat(floor.getFinalPrice()).isEqualByComparingTo("50000");
        QuoteFact malformed = quote(); malformed.setString("includedUnits", "invalid");
        malformed.setInt("actualUnits", 20); malformed.setDecimal("overageRate", BigDecimal.ONE);
        assertThatThrownBy(() -> fire(malformed)).isInstanceOf(RuntimeException.class);
        QuoteFact invalidMargin = quote(); invalidMargin.setString("estimatedMarginPct", "invalid");
        assertThatThrownBy(() -> fire(invalidMargin)).isInstanceOf(RuntimeException.class);
        QuoteFact invalidCredit = quote(); invalidCredit.setBoolean("hasPerpetualEntitlement", true);
        invalidCredit.setBoolean("migrationProgramActive", true); invalidCredit.setString("perpetualBookValue", "invalid");
        assertThatThrownBy(() -> fire(invalidCredit)).isInstanceOf(RuntimeException.class);
        QuoteFact invalidAmendment = quote(); invalidAmendment.setString("dealType", "AMENDMENT");
        invalidAmendment.setInt("remainingTermDays", 90); invalidAmendment.setString("fullTermDays", "invalid");
        invalidAmendment.setDecimal("amendmentDeltaAmount", BigDecimal.ONE);
        assertThatThrownBy(() -> fire(invalidAmendment)).isInstanceOf(RuntimeException.class);
        QuoteFact invalidCoreQuote = quote();
        QuoteLineItemFact invalidCore = QuoteLineItemFact.builder().quote(invalidCoreQuote).lineItemType("ONPREM_CORE").build();
        invalidCore.setString("coreCount", "invalid");
        assertThatThrownBy(() -> fire(invalidCoreQuote, invalidCore)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void preservesOwnerEditedV7BodyAndAllManagedPolicyMetadata() {
        jdbc.update("update pricing.rules set content = content || E'\\n// owner edit', updated_by = 'owner' where uuid = 'rule-002'");
        var before = jdbc.queryForList("select * from pricing.rules order by uuid");
        latest.migrate();
        var after = jdbc.queryForList("select * from pricing.rules order by uuid");
        for (int i = 0; i < before.size(); i++) {
            var original = new java.util.HashMap<>(before.get(i));
            var repaired = new java.util.HashMap<>(after.get(i));
            if (original.get("uuid").equals("rule-002")) assertThat(repaired).isEqualTo(original);
            original.remove("content"); repaired.remove("content");
            assertThat(repaired).isEqualTo(original);
        }
        assertThat(jdbc.queryForObject("select content from pricing.rules where uuid='rule-004'", String.class))
                .contains("getString(\"coreCount\") != null");
    }

    private QuoteFact quote() {
        QuoteFact quote = QuoteFact.builder().externalId("ramp-quote").currency("USD").durationInMonths(36)
                .basePrice(new BigDecimal("77400")).finalPrice(new BigDecimal("77400")).build();
        quote.setDecimal("tcv", new BigDecimal("77400")); quote.setDecimal("acv", new BigDecimal("25800"));
        return quote;
    }

    private QuoteLineItemFact namedLine(QuoteFact quote, int quantity) {
        return QuoteLineItemFact.builder().quote(quote).lineItemType("SAAS_SEAT_NAMED").quantity(quantity)
                .listPrice(new BigDecimal("50")).build();
    }

    private void fire(Object... facts) {
        List<RuleDefinition> rules = jdbc.query("select uuid, name, content from pricing.rules order by priority desc",
                (row, index) -> RuleDefinition.builder().id(row.getString("uuid")).name(row.getString("name"))
                        .content(row.getString("content")).build());
        assertThat(rules).hasSize(11);
        var session = compiler.compileRules(rules).newKieSession();
        try { for (Object fact : facts) session.insert(fact); session.fireAllRules(); }
        finally { session.dispose(); }
    }
}
