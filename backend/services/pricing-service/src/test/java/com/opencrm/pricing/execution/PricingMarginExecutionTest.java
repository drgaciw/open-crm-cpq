package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostBasisStaleness;
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
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.drools.DroolsSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-438 margin slice, end to end through {@link PricingExecutionService}: placeholder parity, cost-basis
 * margin per line and per period, fail closed, the audit and waterfall trace, and replay.
 */
@Tag("unit")
class PricingMarginExecutionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 10, 15).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private CustomerRepository customerRepository;
    private PricingProductRepository productRepository;
    private PriceBookResolutionClient priceBookResolutionClient;
    private PricingAuditLogRepository auditLogRepository;
    private final List<PricingAuditLog> audits = new ArrayList<>();
    private final AtomicInteger lookups = new AtomicInteger();
    private final AtomicReference<String> seatCost = new AtomicReference<>("10.48");
    private final AtomicReference<Long> seatVersion = new AtomicReference<>(1L);

    private final CostBasisLookup fakeLookup = (product, effectiveOn, pricedOn) -> {
        lookups.incrementAndGet();
        if (!"PROD-SEAT".equals(product)) {
            return Optional.empty();
        }
        return Optional.of(new UnitCost(product, "seat", new BigDecimal(seatCost.get()), "USD",
                LocalDate.of(2026, 10, 1), seatVersion.get(),
                Map.of(CostComponent.DIRECT, new BigDecimal(seatCost.get()))));
    };

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(PricingProductRepository.class);
        priceBookResolutionClient = mock(PriceBookResolutionClient.class);
        auditLogRepository = mock(PricingAuditLogRepository.class);
        when(auditLogRepository.save(any(PricingAuditLog.class))).thenAnswer(invocation -> {
            PricingAuditLog log = invocation.getArgument(0);
            log.setUuid("audit-" + (audits.size() + 1));
            audits.addFirst(log);
            return log;
        });
        // The bounded replay query (m2): newest first, cost-basis rows only, at most 20.
        when(auditLogRepository.findTop20ByQuoteExternalIdAndWaterfallTraceIsNotNullOrderByExecutedAtDescIdDesc(
                anyString())).thenAnswer(invocation -> audits.stream()
                .filter(a -> invocation.getArgument(0).equals(a.getQuoteExternalId()) && a.getWaterfallTrace() != null)
                .limit(20).toList());
    }

    private PricingExecutionService service(MarginSource source, CostBasisLookup lookup, Clock clock) {
        RuleSetRepository ruleSetRepository = mock(RuleSetRepository.class);
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        PricingExecutionService service = new PricingExecutionService(customerRepository, productRepository,
                ruleSetRepository, mock(RuleRepository.class), priceBookResolutionClient, auditLogRepository,
                new PricingContextValidator(), new MetricsCalculator(), MAPPER,
                new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler())));
        service.setRampEnabled(true);
        if (source != null) {
            service.setMarginConfiguration(source, new CostBasisMarginEvaluator(lookup, clock));
        }
        return service;
    }

    // ---- placeholder: nothing changes ----

    /**
     * m7: placeholder mode is compared with the execute golden outputs recorded before this wave
     * ({@code fixtures/pricing/execute/*.json}, {@code expected}), through the same harness and normalisation as
     * {@link PricingExecuteGoldenFixtureTest}, not with another code path of this wave.
     */
    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({
            "01-list-price-baseline.json, default", "01-list-price-baseline.json, placeholder",
            "02-multi-line-override.json, default", "02-multi-line-override.json, placeholder",
            "03-override-only.json, default", "03-override-only.json, placeholder",
            "04-evaluate-mapped-happy-path.json, default", "04-evaluate-mapped-happy-path.json, placeholder"})
    @DisplayName("placeholder: execute matches the pre-wave golden output, with no margin evidence or trace")
    void placeholderMatchesExecuteGolden(String fixture, String mode) throws Exception {
        JsonNode root = fixture("execute/" + fixture);
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        stubGolden(root.get("stubs"));
        // The golden harness: a mocked rules engine and no rule sets.
        PricingExecutionService service = new PricingExecutionService(customerRepository, productRepository,
                goldenRuleSets(), mock(RuleRepository.class), priceBookResolutionClient, auditLogRepository,
                new PricingContextValidator(), new MetricsCalculator(), MAPPER, mock(DroolsRulesEngine.class));
        if ("placeholder".equals(mode)) {
            service.setMarginConfiguration(MarginSource.PLACEHOLDER, new CostBasisMarginEvaluator(fakeLookup, CLOCK));
        }

        PricingResult result = service.execute(request);

        // Margin evidence would live in line metadata, which the golden harness strips: check it is absent first.
        assertThat(result.getLineResults()).allSatisfy(line -> assertThat(line.getMetadata() == null
                ? Map.<String, Object>of() : line.getMetadata()).doesNotContainKeys("margin", "marginGovernance"));
        JsonNode expected = root.get("expected").deepCopy();
        ObjectNode actual = MAPPER.valueToTree(result);
        stripGoldenNoise(actual);
        normalizeNumbers(expected);
        normalizeNumbers(actual);
        assertThat(actual.get("success")).isEqualTo(expected.get("success"));
        assertThat(actual.get("approvalState")).isEqualTo(expected.get("approvalState"));
        assertThat(actual.get("lineResults")).isEqualTo(expected.get("lineResults"));
        assertThat(actual.get("waterfall")).isEqualTo(expected.get("waterfall"));
        // Byte-for-byte financial metrics: estimatedMarginPct 40.0, and no marginFraction or marginSource key.
        assertThat(actual.get("financialMetrics")).isEqualTo(expected.get("financialMetrics"));
        assertThat(audits.getFirst().getWaterfallTrace()).isNull();
        assertThat(lookups).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ramp/ac-01-absolute-three-year.json", "ramp/ac-02-quantity-named-seat.json"})
    @DisplayName("placeholder: ramp figures match the pre-wave ramp goldens and margin stays the 40.0 placeholder")
    void placeholderMatchesRampGolden(String fixture) throws Exception {
        PricingResult result = execute(service(MarginSource.PLACEHOLDER, fakeLookup, CLOCK), fixture);
        JsonNode expected = fixture(fixture).get("expected");

        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo(expected.get("tcv").decimalValue());
        assertThat(result.getFinancialMetrics().getAcv()).isEqualByComparingTo(expected.get("acv").decimalValue());
        assertThat(result.getFinancialMetrics().getArr()).isEqualByComparingTo(expected.get("arr").decimalValue());
        assertThat(result.getFinancialMetrics().getExitArr())
                .isEqualByComparingTo(expected.get("exitArr").decimalValue());
        List<PricedPeriod> periods = result.getLineResults().getFirst().getPricedPeriods();
        assertThat(periods).hasSize(expected.get("periodAmounts").size());
        for (int i = 0; i < expected.get("periodAmounts").size(); i++) {
            assertThat(periods.get(i).getPeriodAmount())
                    .isEqualByComparingTo(expected.get("periodAmounts").get(i).decimalValue());
        }
        assertThat(result.getFinancialMetrics().getEstimatedMarginPct()).isEqualByComparingTo("40.0");
        JsonNode metrics = tree(result).get("financialMetrics");
        assertThat(metrics.has("marginFraction")).isFalse();
        assertThat(metrics.has("marginSource")).isFalse();
        assertThat(result.getLineResults()).allSatisfy(line -> assertThat(line.getMetadata())
                .doesNotContainKeys("margin", "marginGovernance"));
        assertThat(audits.getFirst().getWaterfallTrace()).isNull();
        assertThat(lookups).hasValue(0);
    }

    // ---- cost basis ----

    @Test
    @DisplayName("cost-basis on AC-02: margin per period and line, quote margin, and no margin routing")
    void costBasisAc02() throws Exception {
        PricingResult result = execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK),
                "ramp/ac-02-quantity-named-seat.json");

        assertThat(result.getFinancialMetrics().getMarginFraction()).isEqualByComparingTo("0.7217");
        assertThat(result.getFinancialMetrics().getEstimatedMarginPct()).isEqualByComparingTo("72.17");
        assertThat(result.getFinancialMetrics().getMarginSource()).isEqualTo("cost-basis");
        LineMargin line = (LineMargin) result.getLineResults().getFirst().getMetadata().get("margin");
        assertThat(line.getPeriods()).extracting(p -> p.getMarginFraction().toPlainString())
                .containsExactly("0.7380", "0.7182", "0.7182");
        // AC-02's 7% tier lowers the unit price, which ramp governance routes as a step-down; margin adds nothing.
        assertThat(result.getExplanationCodes()).noneMatch(code -> code.startsWith("MARGIN_"));
        assertThat(result.getLineResults().getFirst().getMetadata()).doesNotContainKey("marginGovernance");
        // The ramp golden figures are untouched by the margin mode.
        assertThat(result.getFinancialMetrics().getTcv()).isEqualByComparingTo("135600.00");
    }

    @Test
    @DisplayName("audit and waterfall trace record margin, source, unit cost with effectiveFrom and version")
    void auditTrace() throws Exception {
        execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK), "ramp/ac-02-quantity-named-seat.json");

        JsonNode trace = MAPPER.readTree(audits.getFirst().getWaterfallTrace());
        assertThat(trace.has("waterfall")).isTrue();
        JsonNode margin = trace.get("margin");
        assertThat(margin.get("source").asText()).isEqualTo("cost-basis");
        JsonNode period = margin.get("lines").get(0).get("periods").get(1);
        assertThat(period.get("sequence").asInt()).isEqualTo(2);
        assertThat(period.get("marginFraction").decimalValue()).isEqualByComparingTo("0.7182");
        assertThat(period.get("unitCost").get("unitCost").decimalValue()).isEqualByComparingTo("10.48");
        assertThat(period.get("unitCost").get("effectiveFrom").asText()).isEqualTo("2026-10-01");
        assertThat(period.get("unitCost").get("version").asLong()).isEqualTo(1L);
        assertThat(period.get("costAsOf").asText()).isEqualTo("2027-10-15");
        // The output snapshot carries the same evidence on the line.
        JsonNode output = MAPPER.readTree(audits.getFirst().getOutputSnapshot());
        assertThat(output.at("/lineResults/0/metadata/margin/periods/0/unitCost/version").asLong()).isEqualTo(1L);
        assertThat(output.at("/financialMetrics/marginSource").asText()).isEqualTo("cost-basis");
    }

    @Test
    @DisplayName("missing cost: no margin anywhere (never 40.0), DEAL_DESK, reason names the product, trace records why")
    void missingCostFailsClosed() throws Exception {
        PricingResult result = execute(service(MarginSource.COST_BASIS, (p, effectiveOn, pricedOn) -> Optional.empty(), CLOCK),
                "ramp/ac-02-quantity-named-seat.json");

        assertThat(result.getFinancialMetrics().getEstimatedMarginPct()).isNull();
        assertThat(result.getFinancialMetrics().getMarginFraction()).isNull();
        assertThat(result.getApprovalState()).isEqualTo("PENDING");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_COST_MISSING: PROD-SEAT");
        assertThat(result.getWaterfall().getApprovalState()).isEqualTo("PENDING");
        JsonNode metrics = tree(result).get("financialMetrics");
        assertThat(metrics.get("estimatedMarginPct").isNull()).isTrue();
        assertThat(metrics.has("marginFraction")).isFalse();
        assertThat(metrics.get("marginSource").asText()).isEqualTo("cost-basis");
        JsonNode period = MAPPER.readTree(audits.getFirst().getWaterfallTrace()).at("/margin/lines/0/periods/0");
        assertThat(period.get("missingCostReason").asText()).isEqualTo("MARGIN_COST_MISSING");
        assertThat(period.has("marginFraction")).isFalse();
    }

    @Test
    @DisplayName("AC-16 end to end: one period under the floor routes, and the governing period is named")
    void ac16EndToEnd() throws Exception {
        seatCost.set("26.00"); // p1 net 40.00 → 0.3500 (at floor); p2/p3 net 37.20 → 0.3010 (under)
        PricingResult result = execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK),
                "ramp/ac-02-quantity-named-seat.json");
        assertThat(result.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(result.getExplanationCodes()).contains("MARGIN_BELOW_FLOOR");
        Map<?, ?> governing = (Map<?, ?>) result.getLineResults().getFirst().getMetadata().get("marginGovernance");
        assertThat(governing.get("governingPeriod")).isEqualTo(2);
        assertThat((BigDecimal) governing.get("marginFraction")).isEqualByComparingTo("0.3010");
    }

    // ---- replay ----

    @Test
    @DisplayName("replay: an already-priced quote returns its original cost and margin, not the latest cost basis")
    void replayReturnsOriginalCost() throws Exception {
        PricingResult first = execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK),
                "ramp/ac-09-replay.json");

        seatCost.set("30.00");
        seatVersion.set(2L);
        int lookupsBeforeReplay = lookups.get();
        Clock later = Clock.offset(CLOCK, Duration.ofDays(120));
        PricingResult replay = execute(service(MarginSource.COST_BASIS, fakeLookup, later),
                "ramp/ac-09-replay.json");

        // The cost comes from the audit trace persisted with the priced result, never from the lookup.
        assertThat(lookups.get()).isEqualTo(lookupsBeforeReplay);
        assertThat(replay.getFinancialMetrics().getMarginFraction())
                .isEqualByComparingTo(first.getFinancialMetrics().getMarginFraction());
        LineMargin replayed = (LineMargin) replay.getLineResults().getFirst().getMetadata().get("margin");
        assertThat(replayed.getPeriods()).extracting(p -> p.getUnitCost().getVersion()).containsOnly(1L);
        assertThat(replayed.getPeriods()).extracting(p -> p.getUnitCost().getUnitCost().toPlainString())
                .containsOnly("10.48");
        JsonNode trace = MAPPER.readTree(audits.getFirst().getWaterfallTrace()).get("margin");
        assertThat(trace.get("replayedFromAuditId").asText()).isEqualTo("audit-1");
        assertThat(trace.get("costBasisAsOf").asText()).isEqualTo("2026-10-15");
    }

    @Test
    @DisplayName("M1: a missing cost is not pinned; after the cost lands, re-executing the same input gives a real margin")
    void missingCostIsNotPinned() throws Exception {
        AtomicReference<Boolean> loaded = new AtomicReference<>(false);
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> loaded.get()
                ? fakeLookup.find(product, effectiveOn, pricedOn) : Optional.empty();
        PricingResult missing = execute(service(MarginSource.COST_BASIS, lookup, CLOCK),
                "ramp/ac-02-quantity-named-seat.json");
        assertThat(missing.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(missing.getFinancialMetrics().getMarginFraction()).isNull();

        loaded.set(true);
        PricingResult priced = execute(service(MarginSource.COST_BASIS, lookup, CLOCK),
                "ramp/ac-02-quantity-named-seat.json");

        assertThat(priced.getFinancialMetrics().getMarginFraction()).isEqualByComparingTo("0.7217");
        assertThat(priced.getExplanationCodes()).noneMatch(code -> code.startsWith("MARGIN_"));
        assertThat(MAPPER.readTree(audits.getFirst().getWaterfallTrace()).get("margin").has("replayedFromAuditId"))
                .isFalse();
    }

    @Test
    @DisplayName("M1: an October 2026 pricing re-executed on 2027-04-01 is not replayed: its cost is stale, so it fails closed")
    void staleReplayFailsClosed() throws Exception {
        CostBasisLookup honoursStaleness = (product, effectiveOn, pricedOn) ->
                CostBasisStaleness.isStale(LocalDate.of(2026, 10, 1), pricedOn)
                        ? Optional.empty() : fakeLookup.find(product, effectiveOn, pricedOn);
        PricingResult october = execute(service(MarginSource.COST_BASIS, honoursStaleness, CLOCK),
                "ramp/ac-09-replay.json");
        assertThat(october.getFinancialMetrics().getMarginFraction()).isNotNull();

        Clock april = Clock.fixed(LocalDate.of(2027, 4, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        PricingResult reexecuted = execute(service(MarginSource.COST_BASIS, honoursStaleness, april),
                "ramp/ac-09-replay.json");

        assertThat(reexecuted.getFinancialMetrics().getMarginFraction()).isNull();
        assertThat(reexecuted.getApprovalTier()).isEqualTo("DEAL_DESK");
        assertThat(reexecuted.getExplanationCodes()).contains("MARGIN_COST_MISSING: PROD-SEAT");
        JsonNode margin = MAPPER.readTree(audits.getFirst().getWaterfallTrace()).get("margin");
        assertThat(margin.has("replayedFromAuditId")).isFalse();
        assertThat(margin.get("costBasisAsOf").asText()).isEqualTo("2027-04-01");
    }

    @Test
    @DisplayName("m2: the replay search reads only the bounded query, never every audit row of the quote")
    void replaySearchIsBounded() throws Exception {
        execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK), "ramp/ac-09-replay.json");
        execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK), "ramp/ac-09-replay.json");

        verify(auditLogRepository, atLeastOnce())
                .findTop20ByQuoteExternalIdAndWaterfallTraceIsNotNullOrderByExecutedAtDescIdDesc("Q-RAMP-09");
        verify(auditLogRepository, never()).findByQuoteExternalIdOrderByExecutedAtDesc(anyString());
    }

    @Test
    @DisplayName("replay pin is by identical input: a changed request is priced on the current cost basis")
    void changedInputUsesCurrentCost() throws Exception {
        execute(service(MarginSource.COST_BASIS, fakeLookup, CLOCK), "ramp/ac-09-replay.json");
        seatCost.set("30.00");
        seatVersion.set(2L);

        JsonNode root = fixture("ramp/ac-09-replay.json");
        stub(root);
        PricingContext changed = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        changed.getLineItems().getFirst().getSchedule().forEach(step -> step.setQuantity(60));
        changed.getLineItems().getFirst().setQuantity(60);
        PricingResult repriced = service(MarginSource.COST_BASIS, fakeLookup, CLOCK).execute(changed);

        LineMargin line = (LineMargin) repriced.getLineResults().getFirst().getMetadata().get("margin");
        assertThat(line.getPeriods()).extracting(p -> p.getUnitCost().getVersion()).containsOnly(2L);
        assertThat(MAPPER.readTree(audits.getFirst().getWaterfallTrace()).get("margin").has("replayedFromAuditId"))
                .isFalse();
    }

    @Test
    @DisplayName("simulate in cost-basis mode derives margin without persisting an audit")
    void simulateDoesNotPersist() throws Exception {
        JsonNode root = fixture("ramp/ac-02-quantity-named-seat.json");
        stub(root);
        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        PricingResult result = service(MarginSource.COST_BASIS, fakeLookup, CLOCK).simulate(request);
        assertThat(result.getFinancialMetrics().getMarginFraction()).isEqualByComparingTo("0.7217");
        assertThat(audits).isEmpty();
    }

    // ---- helpers ----

    private PricingResult execute(PricingExecutionService service, String fixture) throws Exception {
        JsonNode root = fixture(fixture);
        stub(root);
        return service.execute(MAPPER.treeToValue(root.get("request"), PricingContext.class));
    }

    private static ObjectNode tree(PricingResult result) {
        ObjectNode node = MAPPER.valueToTree(result);
        node.remove("executionTimeMs");
        return node;
    }

    private void stub(JsonNode root) {
        JsonNode stubs = root.get("stubs");
        JsonNode customerNode = stubs.get("customer");
        Customer customer = Customer.builder()
                .externalId(customerNode.get("externalId").asText())
                .name(customerNode.get("name").asText())
                .region(CustomerRegion.valueOf(customerNode.get("region").asText()))
                .churnScore(customerNode.get("churnScore").asInt())
                .build();
        when(customerRepository.findByExternalIdAndDeletedFalse(customer.getExternalId()))
                .thenReturn(Optional.of(customer));
        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        JsonNode entriesNode = stubs.get("priceBookEntries");
        if (entriesNode != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = entriesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                entries.put(field.getKey(), ProductEntryResolveDto.builder()
                        .entryMatched(field.getValue().path("entryMatched").asBoolean(true))
                        .listPrice(field.getValue().get("listPrice") == null ? null
                                : field.getValue().get("listPrice").decimalValue())
                        .currency("USD").build());
            }
        }
        when(priceBookResolutionClient.resolveBatch(anyString(), anyList()))
                .thenReturn(ResolveBatchResponse.builder().priceBookId(1L).entries(entries)
                        .priceBookCurrency("USD").build());
        when(productRepository.findByExternalIdInAndDeletedFalse(anyCollection())).thenReturn(
                entries.containsKey("PROD-SEAT") ? List.of(PricingProduct.builder().externalId("PROD-SEAT")
                        .productType(ProductType.SAAS_SEAT_NAMED).currency("USD")
                        .listPrice(entries.get("PROD-SEAT").getListPrice()).build()) : List.of());
    }

    private void stubGolden(JsonNode stubs) {
        JsonNode customerNode = stubs.get("customer");
        Customer customer = Customer.builder()
                .externalId(customerNode.get("externalId").asText())
                .name(customerNode.get("name").asText())
                .region(CustomerRegion.valueOf(customerNode.get("region").asText()))
                .churnScore(customerNode.get("churnScore").asInt())
                .build();
        when(customerRepository.findByExternalIdAndDeletedFalse(customer.getExternalId()))
                .thenReturn(Optional.of(customer));
        Map<String, ProductEntryResolveDto> entries = new HashMap<>();
        JsonNode entriesNode = stubs.get("priceBookEntries");
        if (entriesNode != null && entriesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = entriesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                entries.put(field.getKey(), ProductEntryResolveDto.builder()
                        .entryMatched(value.path("entryMatched").asBoolean(true))
                        .listPrice(value.get("listPrice") == null || value.get("listPrice").isNull()
                                ? null : value.get("listPrice").decimalValue())
                        .currency(value.path("currency").asText("USD"))
                        .build());
            }
        }
        when(priceBookResolutionClient.resolveBatch(anyString(), anyList()))
                .thenReturn(ResolveBatchResponse.builder().priceBookId(1L).priceBookCurrency("USD")
                        .entries(entries).build());
    }

    private static RuleSetRepository goldenRuleSets() {
        RuleSetRepository ruleSetRepository = mock(RuleSetRepository.class);
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        return ruleSetRepository;
    }

    /** As {@link PricingExecuteGoldenFixtureTest}: the fields the goldens do not record. */
    private static void stripGoldenNoise(ObjectNode actual) {
        actual.remove("executionTimeMs");
        actual.remove("errorMessage");
        actual.remove("approvalTier");
        JsonNode lines = actual.get("lineResults");
        if (lines != null && lines.isArray()) {
            for (JsonNode line : lines) {
                if (line instanceof ObjectNode objectNode) {
                    objectNode.remove("explanationCodes");
                    objectNode.remove("metadata");
                }
            }
        }
        if (actual.get("waterfall") instanceof ObjectNode waterfallNode) {
            waterfallNode.remove("explanationCodes");
        }
        JsonNode explanationCodes = actual.get("explanationCodes");
        if (explanationCodes != null && explanationCodes.isArray() && explanationCodes.isEmpty()) {
            actual.remove("explanationCodes");
        }
    }

    private static void normalizeNumbers(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            objectNode.fields().forEachRemaining(entries::add);
            for (Map.Entry<String, JsonNode> entry : entries) {
                if (entry.getValue().isNumber()) {
                    objectNode.set(entry.getKey(), MAPPER.getNodeFactory().numberNode(entry.getValue().decimalValue()));
                } else {
                    normalizeNumbers(entry.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                if (arrayNode.get(i).isNumber()) {
                    arrayNode.set(i, MAPPER.getNodeFactory().numberNode(arrayNode.get(i).decimalValue()));
                } else {
                    normalizeNumbers(arrayNode.get(i));
                }
            }
        }
    }

    private static JsonNode fixture(String name) throws Exception {
        try (InputStream in = PricingMarginExecutionTest.class.getResourceAsStream("/fixtures/pricing/" + name)) {
            return MAPPER.readTree(in);
        }
    }
}
