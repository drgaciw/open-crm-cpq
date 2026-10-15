package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostComponent;
import com.opencrm.pricing.cost.UnitCost;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.execution.margin.CostBasisMarginEvaluator;
import com.opencrm.pricing.execution.margin.LineMargin;
import com.opencrm.pricing.execution.margin.MarginSource;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.drools.DroolsSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DRG-438 replay against PostgreSQL: the cost-basis trace is written to the real {@code pricing_audit_log}
 * schema (Flyway), read back from {@code waterfall_trace}, and a replay of the identical quote returns the
 * original unit cost and margin after the cost basis changed. Needs Docker.
 */
@Tag("integration")
@Testcontainers
class MarginReplayAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 10, 15).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private JdbcTemplate jdbc;
    private PricingAuditLogRepository auditLogRepository;
    private final AtomicReference<String> seatCost = new AtomicReference<>("10.48");
    private final AtomicReference<Long> seatVersion = new AtomicReference<>(1L);
    private final CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> Optional.of(new UnitCost(product, "seat",
            new BigDecimal(seatCost.get()), "USD", LocalDate.of(2026, 10, 1), seatVersion.get(),
            Map.of(CostComponent.DIRECT, new BigDecimal(seatCost.get()))));

    @BeforeEach
    void migrate() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(source).schemas("pricing").defaultSchema("pricing")
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(source);

        // The audit repository, backed by the real table.
        auditLogRepository = mock(PricingAuditLogRepository.class);
        when(auditLogRepository.save(any(PricingAuditLog.class))).thenAnswer(invocation -> {
            PricingAuditLog log = invocation.getArgument(0);
            log.setUuid(UUID.randomUUID().toString());
            log.setExecutedAt(Instant.now());
            jdbc.update("INSERT INTO pricing.pricing_audit_log (uuid, quote_external_id, customer_external_id, "
                            + "input_snapshot, output_snapshot, waterfall_trace, execution_time_ms, executed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    log.getUuid(), log.getQuoteExternalId(), log.getCustomerExternalId(), log.getInputSnapshot(),
                    log.getOutputSnapshot(), log.getWaterfallTrace(), log.getExecutionTimeMs(),
                    Timestamp.from(log.getExecutedAt()));
            return log;
        });
        // The SQL Spring Data derives for the bounded replay query (m2).
        when(auditLogRepository.findTop20ByQuoteExternalIdAndWaterfallTraceIsNotNullOrderByExecutedAtDescIdDesc(
                anyString())).thenAnswer(invocation ->
                jdbc.query("SELECT uuid, quote_external_id, input_snapshot, output_snapshot, waterfall_trace "
                                + "FROM pricing.pricing_audit_log WHERE quote_external_id = ? "
                                + "AND waterfall_trace IS NOT NULL ORDER BY executed_at DESC, id DESC LIMIT 20",
                        (rs, row) -> PricingAuditLog.builder().uuid(rs.getString(1)).quoteExternalId(rs.getString(2))
                                .inputSnapshot(rs.getString(3)).outputSnapshot(rs.getString(4))
                                .waterfallTrace(rs.getString(5)).build(),
                        (Object) invocation.getArgument(0)));
    }

    @Test
    void replayAfterCostChangeReturnsPersistedOriginalCost() throws Exception {
        PricingResult first = execute(CLOCK);
        String firstAudit = jdbc.queryForObject(
                "SELECT uuid FROM pricing.pricing_audit_log ORDER BY id DESC LIMIT 1", String.class);

        seatCost.set("30.00");
        seatVersion.set(2L);
        PricingResult replay = execute(Clock.offset(CLOCK, Duration.ofDays(90)));

        assertThat(replay.getFinancialMetrics().getMarginFraction())
                .isEqualByComparingTo(first.getFinancialMetrics().getMarginFraction());
        LineMargin line = (LineMargin) replay.getLineResults().getFirst().getMetadata().get("margin");
        assertThat(line.getPeriods()).extracting(p -> p.getUnitCost().getVersion()).containsOnly(1L);

        String trace = jdbc.queryForObject(
                "SELECT waterfall_trace FROM pricing.pricing_audit_log ORDER BY id DESC LIMIT 1", String.class);
        JsonNode margin = MAPPER.readTree(trace).get("margin");
        assertThat(margin.get("replayedFromAuditId").asText()).isEqualTo(firstAudit);
        assertThat(margin.at("/lines/0/periods/2/unitCost/effectiveFrom").asText()).isEqualTo("2026-10-01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pricing.pricing_audit_log", Integer.class)).isEqualTo(2);
    }

    private PricingResult execute(Clock clock) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/pricing/ramp/ac-09-replay.json")) {
            root = MAPPER.readTree(in);
        }
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("CUST-RAMP-09")).thenReturn(Optional.of(Customer.builder()
                .externalId("CUST-RAMP-09").name("Ramp Nine").region(CustomerRegion.NA).churnScore(10).build()));
        PriceBookResolutionClient priceBook = mock(PriceBookResolutionClient.class);
        when(priceBook.resolveBatch(anyString(), anyList())).thenReturn(ResolveBatchResponse.builder()
                .priceBookId(1L).priceBookCurrency("USD").entries(Map.of("PROD-SEAT", ProductEntryResolveDto.builder()
                        .entryMatched(true).listPrice(new BigDecimal("50.00")).currency("USD").build())).build());
        PricingProductRepository products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(anyCollection())).thenReturn(List.of(PricingProduct.builder()
                .externalId("PROD-SEAT").productType(ProductType.SAAS_SEAT_NAMED).currency("USD")
                .listPrice(new BigDecimal("50.00")).build()));
        RuleSetRepository ruleSets = mock(RuleSetRepository.class);
        when(ruleSets.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any())).thenReturn(new ArrayList<>());

        PricingExecutionService service = new PricingExecutionService(customers, products, ruleSets,
                mock(RuleRepository.class), priceBook, auditLogRepository, new PricingContextValidator(),
                new MetricsCalculator(), MAPPER, new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler())));
        service.setRampEnabled(true);
        service.setMarginConfiguration(MarginSource.COST_BASIS, new CostBasisMarginEvaluator(lookup, clock));
        return service.execute(MAPPER.treeToValue(root.get("request"), PricingContext.class));
    }
}
