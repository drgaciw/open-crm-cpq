package com.opencrm.contract.documents.apply;

import com.opencrm.contract.controller.DocumentApplyController;
import com.opencrm.contract.documents.AmendmentStateMachine;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DRG-621. A later unit's digest conflict must keep earlier commits, block that unit,
 * record PARTIALLY_APPLIED, and return the result (controller HTTP 200). A conflict that
 * blocks every unit still refuses with 409 and leaves the document EXECUTED.
 * DoD v0.3 items 3, 13, 14, 17, and 18.
 */
@Tag("unit")
class DocumentApplyMidLoopConflictTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final String WRONG_DIGEST = "ab".repeat(32);

    @Test
    void unitTwoConflictKeepsUnitOneRecordsPartialAndReturnsTheOutcome() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(result.unitOutcomes()).extracting(
                        ApplyResult.UnitOutcome::unitId,
                        ApplyResult.UnitOutcome::outcome,
                        ApplyResult.UnitOutcome::reason)
                .containsExactly(
                        tuple(1, ApplicationUnitStatus.APPLIED.name(), null),
                        tuple(2, ApplicationUnitStatus.BLOCKED.name(), "DIGEST_CONFLICT"),
                        tuple(3, ApplicationUnitStatus.APPLIED.name(), null));
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.PARTIALLY_APPLIED)
                .allowed()).isTrue();
        assertThat(fixture.units).extracting(unit -> unit.getStatus()).containsExactly(
                ApplicationUnitStatus.APPLIED, ApplicationUnitStatus.BLOCKED, ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(3);
        assertThat(fixture.ledger.get(fixture.document.getId() + ":2").getApplyKeyDigest()).isEqualTo(WRONG_DIGEST);
        assertThat(fixture.periods).hasSize(2);
        assertThat(fixture.periods).extracting(period -> period.getSubscriptionId()).containsExactly(1L, 3L);

        List<AmendmentEventAppend> blocked = fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .toList();
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).reasonCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(blocked.get(0).unitNo()).isEqualTo(2);
        assertThat(fixture.events.stream().filter(event -> "DIGEST_CONFLICT".equals(event.reasonCode()))).hasSize(1);
        assertThat(fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_APPLIED.equals(event.eventType()))
                .map(AmendmentEventAppend::unitNo)).containsExactly(1, 3);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count())
                .isEqualTo(1);
    }

    @Test
    void controllerBodyKeepsDocumentFieldsAndListsUnitOutcomes() throws Exception {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        DocumentApplyController controller = new DocumentApplyController(fixture.orchestrator);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("quote-service")
                .claim("tid", fixture.tenantId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_INVOKE"))));
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
            mvc.perform(post(DocumentApplyController.PATH, fixture.document.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Correlation-Id", "corr-s4"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.documentId").value(fixture.document.getId().toString()))
                    .andExpect(jsonPath("$.data.status").value("PARTIALLY_APPLIED"))
                    .andExpect(jsonPath("$.data.replayed").value(false))
                    .andExpect(jsonPath("$.data.unitOutcomes.length()").value(3))
                    .andExpect(jsonPath("$.data.unitOutcomes[0].unitId").value(1))
                    .andExpect(jsonPath("$.data.unitOutcomes[0].outcome").value("APPLIED"))
                    .andExpect(jsonPath("$.data.unitOutcomes[0].reason").value(nullValue()))
                    .andExpect(jsonPath("$.data.unitOutcomes[1].unitId").value(2))
                    .andExpect(jsonPath("$.data.unitOutcomes[1].outcome").value("BLOCKED"))
                    .andExpect(jsonPath("$.data.unitOutcomes[1].reason").value("DIGEST_CONFLICT"))
                    .andExpect(jsonPath("$.data.unitOutcomes[2].unitId").value(3))
                    .andExpect(jsonPath("$.data.unitOutcomes[2].outcome").value("APPLIED"))
                    .andExpect(jsonPath("$.data.unitOutcomes[2].reason").value(nullValue()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void everyUnitConflictReturns409LeavesExecutedAndWritesBlockedChainAudit() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.ledger.put(fixture.document.getId() + ":1", ledgerRow(fixture, 1));
        fixture.ledger.put(fixture.document.getId() + ":2", ledgerRow(fixture, 2));

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(fixture.units).extracting(unit -> unit.getStatus())
                .containsExactly(ApplicationUnitStatus.PENDING, ApplicationUnitStatus.PENDING);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_BLOCKED)).isZero();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isZero();
        List<AmendmentEventAppend> chain = fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .toList();
        assertThat(chain).extracting(AmendmentEventAppend::unitNo, AmendmentEventAppend::reasonCode,
                        AmendmentEventAppend::documentId)
                .containsExactly(
                        tuple(1, "DIGEST_CONFLICT", fixture.document.getId()),
                        tuple(2, "DIGEST_CONFLICT", fixture.document.getId()));
        assertThat(chain).allSatisfy(event -> assertThat(event.payloadDigest()).matches("[0-9a-f]{64}"));
    }

    @Test
    void replayAfterMidLoopConflictDoesNotApplyAgain() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        fixture.apply();
        int events = fixture.events.size();
        int periods = fixture.periods.size();
        int ledger = fixture.ledger.size();

        ApplyResult replay = fixture.apply();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.units).extracting(unit -> unit.getStatus()).containsExactly(
                ApplicationUnitStatus.APPLIED, ApplicationUnitStatus.BLOCKED, ApplicationUnitStatus.APPLIED);
        assertThat(fixture.events).hasSize(events);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.ledger).hasSize(ledger);
        assertThat(fixture.ledger.get(fixture.document.getId() + ":2").getApplyKeyDigest()).isEqualTo(WRONG_DIGEST);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count())
                .isEqualTo(1);
    }

    @Test
    void changedAppliedUnitIsRefusedAfterPartialApply() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        fixture.apply();
        int periods = fixture.periods.size();
        fixture.units.get(0).setObservedCommercialRevision(99L);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void changedBlockedUnitIsRefusedAfterPartialApply() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        fixture.apply();
        int periods = fixture.periods.size();
        fixture.units.get(1).setObservedCommercialRevision(99L);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void changedBlockedUnitMatchingLedgerIsStillNotAReplay() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        var blocked = fixture.units.get(1);
        blocked.setObservedCommercialRevision(99L);
        String laterDigest = DocumentApplyDigest.unitDigest(fixture.document.getId(), blocked,
                fixture.lines.stream().filter(line -> line.unitNo() == 2).toList());
        blocked.setObservedCommercialRevision(7L);
        fixture.ledger.get(fixture.document.getId() + ":2").setApplyKeyDigest(laterDigest);
        fixture.apply();
        blocked.setObservedCommercialRevision(99L);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void missingBlockedAttemptDigestIsRefusedRatherThanAssumedToMatch() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        fixture.apply();
        fixture.units.get(1).setApplyKeyDigest(null);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void crashRecoveryRejectsChangedAppliedUnitBeforeAnyPendingUnit() {
        ApplyFixture fixture = crashedAfterFirstUnit();
        fixture.units.get(0).setObservedCommercialRevision(99L);
        // Even if a pending sibling sorts before the changed applied unit, it must not be applied.
        fixture.units.get(0).setDependencyOrder(2);
        fixture.units.get(1).setDependencyOrder(1);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void crashRecoveryResumesWhenAppliedUnitIsUnchanged() {
        ApplyFixture fixture = crashedAfterFirstUnit();

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(2);
    }

    @Test
    void recoveryFinalizesAnExecutedDocumentAfterAllUnitsCommitted() {
        ApplyFixture fixture = crashedAfterFirstUnit();
        var second = fixture.units.get(1);
        String digest = DocumentApplyDigest.unitDigest(fixture.document.getId(), second,
                fixture.lines.stream().filter(line -> line.unitNo() == 2).toList());
        second.setStatus(ApplicationUnitStatus.APPLIED);
        markEvidence(fixture, second, null);
        second.setApplyKeyDigest(digest);
        DocumentApplyLedgerEntry entry = ledgerRow(fixture, 2);
        entry.setApplyKeyDigest(digest);
        fixture.ledger.put(fixture.document.getId() + ":2", entry);

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isZero();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.apply().status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isEqualTo(1);
    }

    @Test
    void recoveryRejectsPendingUnitWithMatchingLedger() {
        ApplyFixture fixture = crashedAfterFirstUnit();
        var pending = fixture.units.get(1);
        String digest = DocumentApplyDigest.unitDigest(fixture.document.getId(), pending,
                fixture.lines.stream().filter(line -> line.unitNo() == 2).toList());
        DocumentApplyLedgerEntry entry = ledgerRow(fixture, 2);
        entry.setApplyKeyDigest(digest);
        fixture.ledger.put(fixture.document.getId() + ":2", entry);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isZero();
    }

    @Test
    void mixedLedgerRejectsPendingUnitBeforeProcessingAnySibling() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        var pending = fixture.unit(1, first, 4L);
        var sibling = fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        String digest = DocumentApplyDigest.unitDigest(fixture.document.getId(), pending,
                fixture.lines.stream().filter(line -> line.unitNo() == 1).toList());
        DocumentApplyLedgerEntry entry = ledgerRow(fixture, 1);
        entry.setApplyKeyDigest(digest);
        fixture.ledger.put(fixture.document.getId() + ":1", entry);
        pending.setDependencyOrder(2);
        sibling.setDependencyOrder(1);

        DocumentApplyException ex = catchThrowableOfType(DocumentApplyException.class, fixture::apply);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(pending.getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(sibling.getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void crashRecoveryDoesNotReevaluateCommittedBlockedUnit() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        var blocked = fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        blocked.setApplyKeyDigest(DocumentApplyDigest.unitDigest(fixture.document.getId(), blocked,
                fixture.lines.stream().filter(line -> line.unitNo() == 1).toList()));
        blocked.setStatus(ApplicationUnitStatus.BLOCKED);
        markEvidence(fixture, blocked, "V3");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(blocked.getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.periods).extracting(period -> period.getSubscriptionId()).containsExactly(2L);
        assertThat(fixture.ledger).doesNotContainKey(fixture.document.getId() + ":1");
    }

    private static ApplyFixture crashedAfterFirstUnit() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        var applied = fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        String digest = DocumentApplyDigest.unitDigest(fixture.document.getId(), applied,
                fixture.lines.stream().filter(line -> line.unitNo() == 1).toList());
        applied.setStatus(ApplicationUnitStatus.APPLIED);
        markEvidence(fixture, applied, null);
        applied.setApplyKeyDigest(digest);
        DocumentApplyLedgerEntry entry = ledgerRow(fixture, 1);
        entry.setApplyKeyDigest(digest);
        fixture.ledger.put(fixture.document.getId() + ":1", entry);
        return fixture;
    }

    private static void markEvidence(ApplyFixture fixture, com.opencrm.contract.domain.documents.AmendmentApplicationUnit unit, String reason) {
        unit.setAttemptRevisionNo(fixture.document.getCurrentRevisionNo());
        unit.setAttemptPinFingerprint(DocumentApplyOrchestrator.pinFingerprint(fixture.document, unit, fixture.command()));
        unit.setBlockReason(reason);
    }

    private static ApplyFixture threeUnitsWithUnitTwoConflict() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        Subscription third = fixture.subscription(3L, 5L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.unit(3, third, 5L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(3, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.ledger.put(fixture.document.getId() + ":2", ledgerRow(fixture, 2));
        return fixture;
    }

    private static DocumentApplyLedgerEntry ledgerRow(ApplyFixture fixture, int unitNo) {
        return DocumentApplyLedgerEntry.builder()
                .documentId(fixture.document.getId())
                .unitNo(unitNo)
                .tenantId(fixture.tenantId)
                .applyKeyDigest(WRONG_DIGEST)
                .createdBy("earlier")
                .build();
    }
}
