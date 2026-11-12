package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.integration.pricebook.*;
import com.opencrm.pricing.product.*;
import com.opencrm.pricing.rule.*;
import com.opencrm.pricing.ruleset.*;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic recorded executions: all repository/price-book/rule inputs are pinned, no network or Docker.
 * Expected files are full serialized results, not numeric-normalized trees. Decimal scale, list order,
 * metadata, explanation codes, approval results and nested period waterfalls are part of the contract.
 */
class DeterministicReplayGoldenMasterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String DIR = "/fixtures/pricing/replay/";
    private static final DroolsRulesEngine ENGINE = new DroolsRulesEngine(new DroolsSessionFactory(new DroolsRuleCompiler()));
    private static final String DRL = text("recorded-rules.drl");

    private static String text(String file) {
        try (var in = DeterministicReplayGoldenMasterTest.class.getResourceAsStream(DIR + file)) {
            if (in == null) throw new IllegalStateException("Missing replay recording: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    static Stream<JsonNode> recordings() throws IOException {
        JsonNode rows = MAPPER.readTree(text("scenarios.json"));
        assertThat(rows).hasSize(100);
        return StreamSupport.stream(rows.spliterator(), false);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("recordings")
    void recordedPricingReplaysBitIdentically(JsonNode recording) throws Exception {
        String id = recording.get("id").asText();
        Harness harness = harness(recording);
        PricingResult first = harness.service.execute(request(recording));
        String actual = MAPPER.writeValueAsString(first);
        assertThat(actual.getBytes(StandardCharsets.UTF_8))
                .isEqualTo(text("expected/" + id + ".json").stripTrailing().getBytes(StandardCharsets.UTF_8));
        String replay = MAPPER.writeValueAsString(harness.service.simulate(request(recording)));
        assertThat(replay).isEqualTo(actual);
        // A new orchestrator with freshly deserialized inputs must not rely on in-memory state.
        assertThat(MAPPER.writeValueAsString(harness(recording).service.simulate(request(recording)))).isEqualTo(actual);
        ArgumentCaptor<PricingAuditLog> saved = ArgumentCaptor.forClass(PricingAuditLog.class);
        verify(harness.audit).save(saved.capture());
        PricingAuditLog audit = saved.getValue();
        assertThat(audit.getOutputSnapshot()).isEqualTo(actual);
        assertThat(audit.getExecutedAt()).isEqualTo(Instant.parse(recording.get("instant").asText()));
        when(harness.audit.findById(1L)).thenReturn(Optional.of(audit));
        PricingWaterfall explanation = new WaterfallExplainService(harness.audit, MAPPER)
                .explain(request(recording).getQuoteExternalId(), 1L);
        assertThat(MAPPER.writeValueAsBytes(explanation)).isEqualTo(MAPPER.writeValueAsBytes(first.getWaterfall()));
        verify(harness.ruleSets, times(2)).findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(
                RuleSetStatus.ACTIVE, LocalDate.of(2026, 1, 15));
        assertThat(first.getExecutionTimeMs()).isZero();
    }

    private static PricingContext request(JsonNode recording) throws IOException {
        return MAPPER.treeToValue(recording.get("request"), PricingContext.class);
    }

    private record Harness(PricingExecutionService service, PricingAuditLogRepository audit, RuleSetRepository ruleSets) {}

    private static Harness harness(JsonNode recording) throws IOException {
        CustomerRepository customers = mock(CustomerRepository.class);
        when(customers.findByExternalIdAndDeletedFalse("CUSTOMER-1")).thenReturn(Optional.of(Customer.builder()
                .externalId("CUSTOMER-1").name("Recorded Customer").churnScore(25).build()));
        PricingProductRepository products = mock(PricingProductRepository.class);
        when(products.findByExternalIdInAndDeletedFalse(any())).thenReturn(List.of(PricingProduct.builder()
                .externalId("PRODUCT-1").productType(ProductType.SAAS_SEAT_NAMED).build()));
        RuleSetRepository ruleSets = mock(RuleSetRepository.class);
        when(ruleSets.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(List.of(RuleSet.builder().id(1L).name("Recorded rules").build()));
        RuleRepository rules = mock(RuleRepository.class);
        when(rules.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(any(), any()))
                .thenReturn(List.of(Rule.builder().id(1L).name("Recorded pricing models").priority(100).content(DRL).build()));
        PriceBookResolutionClient books = mock(PriceBookResolutionClient.class);
        when(books.resolveBatch(any(), any())).thenReturn(ResolveBatchResponse.builder()
                .priceBookCurrency(request(recording).getCurrency()).entries(Map.of("PRODUCT-1", ProductEntryResolveDto.builder()
                        .entryMatched(true).listPrice(new java.math.BigDecimal(recording.get("listPrice").asText()))
                        .currency(request(recording).getCurrency()).build())).build());
        PricingAuditLogRepository audit = mock(PricingAuditLogRepository.class);
        PricingExecutionService service = new PricingExecutionService(customers, products, ruleSets, rules, books, audit,
                new PricingContextValidator(), new MetricsCalculator(AcvVersion.TERM_YEARS), MAPPER, ENGINE);
        service.setClock(Clock.fixed(Instant.parse(recording.get("instant").asText()), ZoneOffset.UTC));
        service.setRampEnabled(true);
        return new Harness(service, audit, ruleSets);
    }
}
