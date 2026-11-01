package com.opencrm.quote.lifecycle;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ContinuationEvaluationView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.security.test.TestCallers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-583 phase A. Red tests for AC-S5-1, AC-S5-2, AC-S5-3 and AC-S5-6, plus DoD v0.3
 * items 4, 13 (lifecycle_change_audit), 16, 17, 18 and 20. AC-S5-4, AC-S5-5 and AC-S5-7 are excluded.
 *
 * <p>Intent AMEND is not a CRM DealType. {@code quotes.deal_type} AMENDMENT stays on the excluded
 * continuation facade. This service does not write a quote.
 */
@DisplayName("LifecycleChange phase A")
class LifecycleChangeServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String OTHER_TENANT = "tenant-b";
    private static final LocalDate FROM = LocalDate.parse("2026-11-01");
    private static final LocalDate TO = LocalDate.parse("2027-10-31");
    private static final String SUBSCRIPTION = "9c0e1f22-7a11-4d33-8b44-1e2f3a4b5c6d";

    private final LifecycleChangeRepository changes = mock(LifecycleChangeRepository.class);
    private final LifecycleChangeAuditWriter audit = mock(LifecycleChangeAuditWriter.class);
    private final SubscriptionTargetReader subscriptions = mock(SubscriptionTargetReader.class);
    private final PricingServiceClient pricing = mock(PricingServiceClient.class);
    private final Map<UUID, LifecycleChange> stored = new HashMap<>();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void store() {
        when(subscriptions.read(any())).thenAnswer(invocation ->
                new SubscriptionTarget(invocation.getArgument(0), "ACTIVE", 4L));
        when(changes.save(any())).thenAnswer(invocation -> {
            LifecycleChange change = invocation.getArgument(0);
            if (change.getVersion() == null) {
                change.setVersion(0L);
            }
            stored.put(change.getId(), change);
            return change;
        });
        when(changes.findByIdAndTid(any(), any())).thenAnswer(invocation -> {
            LifecycleChange change = stored.get(invocation.getArgument(0));
            if (change == null || !change.getTid().equals(invocation.getArgument(1))) {
                return Optional.empty();
            }
            return Optional.of(change);
        });
        when(changes.findByTidAndIdempotencyKey(any(), any())).thenAnswer(invocation -> stored.values().stream()
                .filter(change -> invocation.getArgument(0).equals(change.getTid()))
                .filter(change -> invocation.getArgument(1).equals(change.getIdempotencyKey()))
                .findFirst());
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
        stored.clear();
    }

    @Test
    @DisplayName("AC-S5-1: Amend with two lines stores action codes, Delta/Absolute and observed revisions")
    void createStoresTwoLines() {
        String actor = LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeResult result = service().create(twoLines(), null);

        assertThat(result.replayed()).isFalse();
        LifecycleChangeResponse body = result.body();
        assertThat(body.getIntent()).isEqualTo("AMEND");
        assertThat(body.getStatus()).isEqualTo("DRAFT");
        assertThat(body.getRevision()).isEqualTo(1);
        assertThat(body.getVersion()).isZero();
        assertThat(body.getLines()).hasSize(2);
        assertThat(body.getLines().get(0).getActionCode()).isEqualTo("UPDATE");
        assertThat(body.getLines().get(0).getQuantitySemantics()).isEqualTo("DELTA");
        assertThat(body.getLines().get(0).getObservedCommercialRevision()).isEqualTo(4L);
        assertThat(body.getLines().get(0).getStartQty()).isEqualTo("10.0000");
        assertThat(body.getLines().get(0).getDeltaQty()).isEqualTo("5.0000");
        assertThat(body.getLines().get(0).getEndQty()).isEqualTo("15.0000");
        assertThat(body.getLines().get(1).getActionCode()).isEqualTo("ADD");
        assertThat(body.getLines().get(1).getQuantitySemantics()).isEqualTo("ABSOLUTE");
        assertThat(body.getCreatedBy()).isEqualTo(actor);

        LifecycleChange saved = stored.values().iterator().next();
        assertThat(saved.getTid()).isEqualTo(TENANT);
        assertThat(saved.getLines()).extracting(ChangeLine::getActionCode).containsExactly("UPDATE", "ADD");
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 13/20: tid is the caller's tenant even when the body supplies another value")
    void tidComesFromTheCaller() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        CreateLifecycleChangeRequest request = twoLines();
        request.setTid(OTHER_TENANT);

        service().create(request, null);

        assertThat(stored.values()).singleElement().extracting(LifecycleChange::getTid).isEqualTo(TENANT);
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 4: Delta must satisfy start + delta = end after scale-4 half-up rounding")
    void deltaEquationAndRounding() {
        LifecycleCallers.signIn("seller", TENANT, "DealDesk");
        CreateLifecycleChangeRequest request = twoLines();
        request.getLines().get(0).setStartQty("1.23456");
        request.getLines().get(0).setDeltaQty("0.0000");
        request.getLines().get(0).setEndQty("1.23456");

        LifecycleChangeResponse body = service().create(request, null).body();

        assertThat(body.getLines().get(0).getStartQty()).isEqualTo("1.2346");
        assertThat(body.getLines().get(0).getEndQty()).isEqualTo("1.2346");
        assertThat(new BigDecimal(body.getLines().get(0).getStartQty())
                .add(new BigDecimal(body.getLines().get(0).getDeltaQty())))
                .isEqualByComparingTo(body.getLines().get(0).getEndQty());
    }

    @Test
    @DisplayName("DoD 4: a Delta line whose rounded quantities do not add is rejected with no audit row")
    void deltaMismatchIsRejectedWithoutAnAuditRow() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        CreateLifecycleChangeRequest request = twoLines();
        request.getLines().get(0).setEndQty("12.0000");

        assertThatThrownBy(() -> service().create(request, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("VALIDATION_REJECTED");
        assertThat(stored).isEmpty();
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 4: Absolute stores end quantity without requiring start + delta = end")
    void absoluteDoesNotApplyTheDeltaEquation() {
        LifecycleCallers.signIn("seller", TENANT, "SalesVP");
        CreateLifecycleChangeRequest request = twoLines();
        ChangeLineWrite add = request.getLines().get(1);
        add.setStartQty("10.0000");
        add.setDeltaQty("1.0000");
        add.setEndQty("3.0000");

        ChangeLineResponse storedLine = service().create(request, null).body().getLines().get(1);

        assertThat(storedLine.getQuantitySemantics()).isEqualTo("ABSOLUTE");
        assertThat(storedLine.getEndQty()).isEqualTo("3.0000");
    }

    @Test
    @DisplayName("DoD 4: effective dates are inclusive; an end before the start is rejected")
    void dateBoundaries() {
        LifecycleCallers.signIn("seller", TENANT, "CFO");
        CreateLifecycleChangeRequest sameDay = twoLines();
        sameDay.getLines().forEach(line -> line.setEffectiveTo(FROM));
        assertThat(service().create(sameDay, null).body().getLines())
                .allSatisfy(line -> assertThat(line.getEffectiveTo()).isEqualTo(FROM));

        stored.clear();
        CreateLifecycleChangeRequest backwards = twoLines();
        backwards.getLines().get(0).setEffectiveTo(FROM.minusDays(1));
        assertThatThrownBy(() -> service().create(backwards, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("VALIDATION_REJECTED");
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("AC-S5-2: RetainContracted + Daily stores the decision ids and leaves configuration hash null")
    void priceRetainsContractedDailyPins() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeService service = service();
        UUID id = service.create(twoLines(), null).body().getId();
        when(pricing.evaluateContinuation(any())).thenAnswer(invocation -> priced(invocation.getArgument(0)));

        LifecycleChangeResponse priced = service.price(id, priceCommand(0L)).body();

        assertThat(priced.getStatus()).isEqualTo("PRICED");
        assertThat(priced.getLines()).allSatisfy(line -> {
            assertThat(line.getAmendMode()).isEqualTo("RETAIN_CONTRACTED");
            assertThat(line.getProrationMethod()).isEqualTo("DAILY");
            assertThat(line.getPolicyDecisionId()).startsWith("dec-");
            assertThat(line.getPolicyId()).isEqualTo("policy-amend");
            assertThat(line.getPolicyVersion()).isEqualTo("9");
            assertThat(line.getPricingTraceId()).isEqualTo("trace-1");
            assertThat(line.getProductExternalId()).isEqualTo("sku-seat");
            assertThat(line.getPricebookVersion()).isEqualTo("pb-2026.10");
            assertThat(line.getConfigurationHash()).isNull();
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> sent = ArgumentCaptor.forClass(Map.class);
        verify(pricing).evaluateContinuation(sent.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) sent.getValue().get("lines");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.get("amendMode")).isEqualTo("RETAIN_CONTRACTED");
            assertThat(line.get("prorationMethod")).isEqualTo("DAILY");
            assertThat(line.get("effectiveFrom")).isEqualTo(FROM.toString());
            assertThat(line.get("effectiveTo")).isEqualTo(TO.toString());
        });
        assertThat(lines.get(0).get("quantitySemantics")).isEqualTo("DELTA");
        assertThat(lines.get(1).get("quantitySemantics")).isEqualTo("ABSOLUTE");
        assertThat(lines.get(1).get("endQuantity")).isEqualTo("2.0000");
        verify(audit, never()).record(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SWAP", "UPGRADE", "DOWNGRADE"})
    @DisplayName("AC-S5-3: submit of Swap, Upgrade or Downgrade writes one audit row and leaves status unchanged")
    void unsupportedActionIsRejected(String action) {
        String actor = LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeService service = service();
        CreateLifecycleChangeRequest request = twoLines();
        request.getLines().get(0).setActionCode(action);
        UUID id = service.create(request, null).body().getId();

        assertThatThrownBy(() -> service.submit(id, version(0L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(business.getErrorCode()).isEqualTo("ACTION_NOT_SUPPORTED_IN_SLICE");
                });

        assertThat(stored.get(id).getStatus()).isEqualTo(LifecycleChangeStatus.DRAFT);
        ArgumentCaptor<LifecycleChangeAuditEntry> captor = ArgumentCaptor.forClass(LifecycleChangeAuditEntry.class);
        verify(audit, times(1)).record(captor.capture());
        LifecycleChangeAuditEntry row = captor.getValue();
        assertThat(row.tid()).isEqualTo(TENANT);
        assertThat(row.lifecycleChangeId()).isEqualTo(id);
        assertThat(row.subscriptionId()).isEqualTo(SUBSCRIPTION);
        assertThat(row.decisionKind()).isEqualTo("LIFECYCLE_CHANGE_REJECTED");
        assertThat(row.reasonCode()).isEqualTo("ACTION_NOT_SUPPORTED_IN_SLICE");
        assertThat(row.decidedBy()).isEqualTo(actor);
        assertThat(row.inputsJson()).isNotBlank();
        assertThat(row.outputsJson()).contains("ACTION_NOT_SUPPORTED_IN_SLICE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "PENDING", "CANCELLED"})
    @DisplayName("AC-S5-6: a target that is not ACTIVE is rejected with one audit row and no lifecycle change")
    void inactiveSubscriptionIsRejected(String status) {
        String actor = LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        when(subscriptions.read(SUBSCRIPTION)).thenReturn(new SubscriptionTarget(SUBSCRIPTION, status, 4L));
        CreateLifecycleChangeRequest request = twoLines();
        request.setTid(OTHER_TENANT);

        assertThatThrownBy(() -> service().create(request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(business.getErrorCode()).isEqualTo("VALIDATION_REJECTED");
                    assertThat(business.getMessage()).contains("V2");
                });

        assertThat(stored).isEmpty();
        ArgumentCaptor<LifecycleChangeAuditEntry> captor = ArgumentCaptor.forClass(LifecycleChangeAuditEntry.class);
        verify(audit, times(1)).record(captor.capture());
        LifecycleChangeAuditEntry row = captor.getValue();
        assertThat(row.tid()).isEqualTo(TENANT);
        assertThat(row.lifecycleChangeId()).isNull();
        assertThat(row.subscriptionId()).isEqualTo(SUBSCRIPTION);
        assertThat(row.decisionKind()).isEqualTo("LIFECYCLE_CHANGE_REJECTED");
        assertThat(row.reasonCode()).isEqualTo("SUBSCRIPTION_NOT_ACTIVE");
        assertThat(row.decidedBy()).isEqualTo(actor);
        assertThat(row.inputsJson()).contains(OTHER_TENANT);
        assertThat(row.outputsJson()).contains("V2");
    }

    @Test
    @DisplayName("DoD 18: price then submit moves DRAFT to PRICED to IN_APPROVAL and writes no audit row")
    void happyPathStatusesWriteNoAudit() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeService service = service();
        when(pricing.evaluateContinuation(any())).thenAnswer(invocation -> priced(invocation.getArgument(0)));
        UUID id = service.create(twoLines(), "idem-1").body().getId();
        assertThat(service.price(id, priceCommand(0L)).body().getStatus()).isEqualTo("PRICED");

        LifecycleChangeResult submitted = service.submit(id, version(0L));

        assertThat(submitted.replayed()).isFalse();
        assertThat(submitted.body().getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(service.submit(id, version(0L)).replayed()).isTrue();
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 18: submit before price is an illegal transition and writes no audit row")
    void submitBeforePriceIsIllegal() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        UUID id = service().create(twoLines(), null).body().getId();

        assertThatThrownBy(() -> service().submit(id, version(0L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("ILLEGAL_TRANSITION");
        verify(audit, never()).record(any());
        assertThat(stored.get(id).getStatus()).isEqualTo(LifecycleChangeStatus.DRAFT);
    }

    @Test
    @DisplayName("DoD 17: the same idempotency key and body replays; a different body conflicts")
    void idempotencyKey() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeService service = service();
        LifecycleChangeResult first = service.create(twoLines(), "key-1");
        LifecycleChangeResult replay = service.create(twoLines(), "key-1");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.body().getId()).isEqualTo(first.body().getId());
        assertThat(stored).hasSize(1);

        CreateLifecycleChangeRequest changed = twoLines();
        changed.getLines().get(1).setEndQty("4.0000");
        changed.getLines().get(1).setDeltaQty("4.0000");
        assertThatThrownBy(() -> service.create(changed, "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("DoD 20: another tenant cannot read or submit this lifecycle change")
    void crossTenantIsNotFound() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        UUID id = service().create(twoLines(), null).body().getId();
        LifecycleCallers.signIn("other", OTHER_TENANT, "CPQ.User");

        assertThatThrownBy(() -> service().get(id))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> service().submit(id, version(0L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 16: anonymous, service token and Admin-only are refused; a CPQ role is allowed")
    void authorization() {
        assertThatThrownBy(() -> service().create(twoLines(), null))
                .satisfies(error -> assertStatus(error, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));

        TestCallers.signInService("quote-service", "Internal.Invoke");
        assertThatThrownBy(() -> service().create(twoLines(), null))
                .satisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN, "LIFECYCLE_CHANGE_MUST_BE_A_USER"));
        TestCallers.clear();

        LifecycleCallers.signIn("admin", TENANT, "Admin");
        assertThatThrownBy(() -> service().submit(UUID.randomUUID(), version(0L)))
                .satisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN, "LIFECYCLE_ROLE_REQUIRED"));

        LifecycleCallers.signIn("nobody", TENANT);
        assertThatThrownBy(() -> service().create(twoLines(), null))
                .satisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN, "LIFECYCLE_ROLE_REQUIRED"));
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 4/20: null configuration hash may be submitted; a missing pricebook pin fails V9 with no audit")
    void configurationHashMayBeNullUntilDrg428() {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        LifecycleChangeService service = service();
        when(pricing.evaluateContinuation(any())).thenAnswer(invocation -> priced(invocation.getArgument(0)));
        UUID id = service.create(twoLines(), null).body().getId();
        service.price(id, priceCommand(0L));
        assertThat(service.submit(id, version(0L)).body().getLines())
                .allSatisfy(line -> assertThat(line.getConfigurationHash()).isNull());

        stored.clear();
        UUID unpinned = service.create(twoLines(), "other-key").body().getId();
        service.price(unpinned, priceCommand(0L));
        stored.get(unpinned).getLines().get(0).setPricebookVersion(null);
        stored.get(unpinned).setStatus(LifecycleChangeStatus.PRICED);

        assertThatThrownBy(() -> service.submit(unpinned, version(0L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getMessage()).contains("V9"));
        verify(audit, never()).record(any());
    }

    @Test
    @DisplayName("DoD 21: create and rejection each log one line")
    void logsCreateAndRejection() {
        Logger logger = (Logger) LoggerFactory.getLogger(LifecycleChangeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
            service().create(twoLines(), null);
            when(subscriptions.read(SUBSCRIPTION)).thenReturn(new SubscriptionTarget(SUBSCRIPTION, "SUSPENDED", 4L));
            assertThatThrownBy(() -> service().create(twoLines(), null)).isInstanceOf(BusinessException.class);
            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("LifecycleChange created"));
            assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("LifecycleChange rejected"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("audit insert commits on its own so a thrown rejection does not roll the row back")
    void auditWriterRequiresNewTransaction() throws Exception {
        var method = JdbcLifecycleChangeAuditWriter.class.getMethod("record", LifecycleChangeAuditEntry.class);
        var transactional = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        assertThat(JdbcLifecycleChangeAuditWriter.INSERT).contains("lifecycle_change_audit");
        assertThat(JdbcLifecycleChangeAuditWriter.INSERT).doesNotContain("commercial_decision_audit");
        assertThat(JdbcLifecycleChangeAuditWriter.INSERT).doesNotContain("quote_id");
    }

    private LifecycleChangeService service() {
        return new LifecycleChangeService(changes, audit, subscriptions, pricing, json);
    }

    private static void assertStatus(Throwable error, HttpStatus status, String code) {
        assertThat(error).isInstanceOf(BusinessException.class);
        BusinessException business = (BusinessException) error;
        assertThat(business.getStatus()).isEqualTo(status);
        assertThat(business.getErrorCode()).isEqualTo(code);
    }

    private static VersionedCommand version(long version) {
        return VersionedCommand.builder().version(version).build();
    }

    private static PriceLifecycleChangeRequest priceCommand(long version) {
        return PriceLifecycleChangeRequest.builder().version(version).build();
    }

    private static CreateLifecycleChangeRequest twoLines() {
        return CreateLifecycleChangeRequest.builder()
                .intent("AMEND")
                .initiationTrigger("MANUAL")
                .actionDate(FROM)
                .contractEffectiveDate(FROM)
                .contractId(UUID.fromString("6b1c2a10-0c3a-4b7e-9f11-2a6e0c9d4b21"))
                .currencyCode("USD")
                .outputMode("QUOTE")
                .tid("attacker-tenant")
                .lines(new ArrayList<>(List.of(updateLine(), addLine())))
                .build();
    }

    private static ChangeLineWrite updateLine() {
        return ChangeLineWrite.builder()
                .actionCode("UPDATE")
                .quantitySemantics("DELTA")
                .targetSubscriptionId(SUBSCRIPTION)
                .sourceAcceptedLineUuid("line-accepted-1")
                .observedCommercialRevision(4L)
                .startQty("10.0000")
                .deltaQty("5.0000")
                .endQty("15.0000")
                .effectiveFrom(FROM)
                .effectiveTo(TO)
                .productExternalId("sku-seat")
                .build();
    }

    private static ChangeLineWrite addLine() {
        return ChangeLineWrite.builder()
                .actionCode("ADD")
                .quantitySemantics("ABSOLUTE")
                .startQty("0.0000")
                .deltaQty("2.0000")
                .endQty("2.0000")
                .effectiveFrom(FROM)
                .effectiveTo(TO)
                .productExternalId("sku-seat")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse<ContinuationEvaluationView> priced(Map<String, Object> request) {
        List<Map<String, Object>> lines = (List<Map<String, Object>>) request.get("lines");
        ContinuationEvaluationView view = new ContinuationEvaluationView();
        view.setTraceId("trace-1");
        view.setPolicyId("policy-amend");
        view.setPolicyVersion("9");
        view.setLines(lines.stream().map(line -> {
            ContinuationEvaluationView.Line pricedLine = new ContinuationEvaluationView.Line();
            pricedLine.setLineRef(String.valueOf(line.get("lineRef")));
            pricedLine.setPolicyDecisionId("dec-" + line.get("lineRef"));
            pricedLine.setPolicyId("policy-amend");
            pricedLine.setPolicyVersion("9");
            pricedLine.setTraceId("trace-1");
            pricedLine.setProductExternalId("sku-seat");
            pricedLine.setPricebookVersion("pb-2026.10");
            pricedLine.setConfigurationHash(null);
            pricedLine.setProcedureStatus("PRICED");
            pricedLine.setUnitRate("40.0000");
            pricedLine.setAmount("100.0000");
            return pricedLine;
        }).toList());
        return ApiResponse.success(view);
    }
}
