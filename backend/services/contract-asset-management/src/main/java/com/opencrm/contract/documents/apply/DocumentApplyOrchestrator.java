package com.opencrm.contract.documents.apply;

import com.opencrm.common.commercial.CommercialDigest;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.documents.AmendmentDocumentService;
import com.opencrm.contract.documents.Transition;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
import com.opencrm.contract.asset.AssetChangeApply;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.DocumentApplyLedgerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Document apply. One unit per subscription, one transaction per unit, dependency order.
 * A failing unit does not undo earlier units. A later call is a separate invocation.
 * A digest conflict after a committed unit is blocked in its own transaction and does not escape the loop.
 */
@Service
public class DocumentApplyOrchestrator {

    public static final String EVENT_UNIT_APPLIED = "APPLY_UNIT_APPLIED";
    public static final String EVENT_UNIT_BLOCKED = "APPLY_UNIT_BLOCKED";
    public static final String EVENT_DOCUMENT_APPLIED = "APPLY_DOCUMENT_APPLIED";
    public static final String EVENT_DOCUMENT_PARTIAL = "APPLY_DOCUMENT_PARTIALLY_APPLIED";
    public static final String EVENT_DOCUMENT_BLOCKED = "APPLY_DOCUMENT_BLOCKED";
    public static final String EVENT_REFUSED = "APPLY_REFUSED";
    public static final String UNITS_METRIC = "opencrm.document.apply.units";
    public static final String ALERT_METRIC = "opencrm.document.apply.alert";
    /** Alert when this counter increments for APPLY_BLOCKED or PARTIALLY_APPLIED. No p95 target is set. */
    public static final String ALERT_CONDITION =
            "Alert when opencrm.document.apply.alert increments for APPLY_BLOCKED or PARTIALLY_APPLIED. No p95 target is set.";

    private static final Logger log = LoggerFactory.getLogger(DocumentApplyOrchestrator.class);

    private final AmendmentDocumentRepository documents;
    private final AmendmentApplicationUnitRepository units;
    private final DocumentApplyLedgerRepository ledger;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRatePeriodRepository rates;
    private final AmendmentEventWriter events;
    private final AmendmentDocumentService documentService;
    private final DocumentApplyGateway gateway;
    private final MeterRegistry meters;
    private final TransactionTemplate unitTransaction;
    private final EntityManager entityManager;
    /** Absent in tests that construct the orchestrator directly. Production injects the DRG-655 mapper. */
    private AssetChangeApply assetChanges;

    @Autowired
    public DocumentApplyOrchestrator(AmendmentDocumentRepository documents,
                                     AmendmentApplicationUnitRepository units,
                                     DocumentApplyLedgerRepository ledger,
                                     SubscriptionRepository subscriptions,
                                     SubscriptionRatePeriodRepository rates,
                                     AmendmentEventWriter events,
                                     AmendmentDocumentService documentService,
                                     DocumentApplyGateway gateway,
                                     MeterRegistry meters,
                                     PlatformTransactionManager transactions,
                                     EntityManager entityManager) {
        this.documents = documents;
        this.units = units;
        this.ledger = ledger;
        this.subscriptions = subscriptions;
        this.rates = rates;
        this.events = events;
        this.documentService = documentService;
        this.gateway = gateway;
        this.meters = meters;
        this.entityManager = entityManager;
        this.unitTransaction = new TransactionTemplate(transactions);
        this.unitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Autowired(required = false)
    void setAssetChanges(AssetChangeApply assetChanges) {
        this.assetChanges = assetChanges;
    }

    public ApplyResult apply(ApplyCommand command) {
        AmendmentDocument document = documents.findById(command.amendmentId())
                .orElseThrow(() -> unknown(command.amendmentId()));
        if (command.tenantId() == null) {
            audit(document, command, "MISSING_TID", "Tenant is not on the verified token");
            throw new DocumentApplyException("Tenant is not on the verified token", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        if (!document.getTenantId().equals(command.tenantId())) {
            audit(document, command, "CROSS_TENANT", "cross-tenant apply");
            throw unknown(document.getId());
        }
        Preflight preflight = unitTransaction.execute(status -> classifyUnderLock(command.amendmentId(), command));
        if (preflight == null) throw new IllegalStateException("apply preflight did not complete");
        document = preflight.document();
        List<AmendmentApplicationUnit> ordered = preflight.units();
        if (preflight.kind() == PreflightKind.CONFLICT) {
            audit(document, command, "DIGEST_CONFLICT", "apply attempt is changed or unverifiable");
            throw new DocumentApplyException("apply attempt is changed or unverifiable", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (preflight.kind() == PreflightKind.REPLAY) {
            if (preflight.recoveredOutcome() != null) count(preflight.recoveredOutcome());
            return preflight.result();
        }
        if (preflight.kind() == PreflightKind.ALL_LEDGER_CONFLICT) {
            AmendmentDocument lockedDocument = document;
            unitTransaction.executeWithoutResult(status -> {
                for (AmendmentApplicationUnit unit : ordered) {
                    events.appendApplyDecision(event(lockedDocument, command, EVENT_UNIT_BLOCKED, null, null,
                            unit.getUnitNo(), "DIGEST_CONFLICT",
                            "apply:DIGEST_CONFLICT:apply key digest does not match the ledger"));
                }
            });
            throw new DocumentApplyException("apply key digest does not match the ledger", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (preflight.kind() == PreflightKind.NOT_EXECUTED) {
            audit(document, command, "NOT_EXECUTED", "apply is refused unless the document is EXECUTED");
            throw new DocumentApplyException("apply is refused unless the document is EXECUTED",
                    HttpStatus.valueOf(preflight.permission().httpStatus()), preflight.permission().errorCode());
        }

        final AmendmentDocument applyDocument = document;
        boolean changed = false;
        for (AmendmentApplicationUnit unit : ordered) {
            try {
                changed |= Boolean.TRUE.equals(unitTransaction.execute(
                        status -> applyUnit(applyDocument, unit, command)));
            } catch (DocumentApplyException ex) {
                if (ex instanceof PendingLedgerInconsistency) {
                    audit(document, command, "DIGEST_CONFLICT", "ledger exists for a unit that is not applied");
                    throw ex;
                }
                if (!"DIGEST_CONFLICT".equals(ex.getErrorCode())) {
                    throw ex;
                }
                // Nothing has committed yet: keep the pre-loop refusal (409, no outcome change).
                if (!changed && !anyApplied(document)) {
                    audit(document, command, "DIGEST_CONFLICT", "apply key digest does not match the ledger");
                    throw ex;
                }
                try {
                    changed |= Boolean.TRUE.equals(unitTransaction.execute(
                            status -> blockDigestConflict(applyDocument, unit, command)));
                } catch (DocumentApplyException terminalConflict) {
                    audit(document, command, "DIGEST_CONFLICT", "apply key digest does not match the recorded attempt");
                    throw terminalConflict;
                }
            }
        }
        Finalization finalization;
        try {
            finalization = unitTransaction.execute(status -> finalizeUnderLock(applyDocument.getId(), command));
        } catch (DocumentApplyException ex) {
            audit(document, command, "DIGEST_CONFLICT", "apply outcome is inconsistent");
            throw ex;
        }
        if (finalization == null) throw new IllegalStateException("apply finalization did not complete");
        if (finalization.recorded()) {
            count(finalization.outcome());
            log.info("document apply documentId={} status={} alert={}", document.getId(), finalization.outcome(), ALERT_CONDITION);
        }
        return new ApplyResult(finalization.outcome(), !finalization.recorded() && !changed, finalization.units());
    }

    /**
     * Present when at least one unit is BLOCKED. Applied siblings are included.
     * BLOCKED is the persisted form of the OQ5 APPLY_BLOCKED unit state.
     */
    private List<ApplyResult.UnitOutcome> unitOutcomes(AmendmentDocument document) {
        List<ApplyResult.UnitOutcome> rows = new ArrayList<>();
        boolean anyBlocked = false;
        for (AmendmentApplicationUnit unit : orderedUnits(document)) {
            if (unit.getStatus() == ApplicationUnitStatus.APPLIED) {
                rows.add(new ApplyResult.UnitOutcome(unit.getUnitNo(), ApplicationUnitStatus.APPLIED.name(), null));
            } else if (unit.getStatus() == ApplicationUnitStatus.BLOCKED) {
                anyBlocked = true;
                rows.add(new ApplyResult.UnitOutcome(unit.getUnitNo(), ApplicationUnitStatus.BLOCKED.name(), unit.getBlockReason()));
            }
        }
        return anyBlocked ? rows : null;
    }

    /** Called first in each locked transaction, before any unit or audit mutation. */
    private void lockAndRefresh(UUID documentId) {
        gateway.lockDocument(documentId);
        // A request-bound EntityManager can retain stale entities across REQUIRES_NEW transactions.
        entityManager.clear();
    }

    private enum PreflightKind { PROCEED, REPLAY, CONFLICT, ALL_LEDGER_CONFLICT, NOT_EXECUTED }
    private record Preflight(PreflightKind kind, AmendmentDocument document, List<AmendmentApplicationUnit> units,
                             List<ApplyLine> lines, ApplyResult result, Transition permission,
                             AmendmentDocumentStatus recoveredOutcome) {}

    private record Finalization(AmendmentDocumentStatus outcome, boolean recorded,
                                List<ApplyResult.UnitOutcome> units) {}

    /** Recheck every unit under the document lock before recording a terminal outcome. */
    private Finalization finalizeUnderLock(UUID id, ApplyCommand command) {
        lockAndRefresh(id);
        AmendmentDocument current = documents.findById(id).orElseThrow();
        List<AmendmentApplicationUnit> ordered = orderedUnits(current);
        List<ApplyLine> lines = gateway.lines(id, current.getCurrentRevisionNo());
        if (!current.getTenantId().equals(command.tenantId())
                || current.getCurrentRevisionNo() != attemptRevision(current)
                || ordered.isEmpty()
                || ordered.stream().anyMatch(unit -> unit.getStatus() == ApplicationUnitStatus.PENDING
                    || !current.getTenantId().equals(unit.getTenantId()) || hasForeignTenantLedger(current, unit))
                || !matchesTerminalAttempt(current, ordered, lines, command)) {
            throw new DocumentApplyException("apply outcome is inconsistent", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        AmendmentDocumentStatus outcome = outcome(ordered);
        boolean recorded;
        if (current.getStatus() == AmendmentDocumentStatus.EXECUTED) {
            recorded = recordOutcomeLocked(current, outcome, command);
            if (!recorded) throw new DocumentApplyException("apply outcome could not be recorded", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        } else if (current.getStatus() == outcome) {
            recorded = false;
        } else {
            throw new DocumentApplyException("apply outcome differs from stored status", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        return new Finalization(outcome, recorded, unitOutcomes(current));
    }

    /** All replay classification reads share the same document advisory lock as unit writes. */
    private Preflight classifyUnderLock(UUID id, ApplyCommand command) {
        lockAndRefresh(id);
        AmendmentDocument current = documents.findById(id).orElseThrow();
        List<AmendmentApplicationUnit> ordered = orderedUnits(current);
        List<ApplyLine> lines = gateway.lines(id, current.getCurrentRevisionNo());
        if (!current.getTenantId().equals(command.tenantId())
                || ordered.stream().anyMatch(unit -> !current.getTenantId().equals(unit.getTenantId())
                    || hasForeignTenantLedger(current, unit))
                || current.getCurrentRevisionNo() != attemptRevision(current)
                || !matchesTerminalAttempt(current, ordered, lines, command)) {
            return new Preflight(PreflightKind.CONFLICT, current, ordered, lines, null, null, null);
        }
        for (AmendmentApplicationUnit unit : ordered) {
            if (unit.getStatus() == ApplicationUnitStatus.PENDING && pendingMatchesLedger(current, unit, lines)) {
                return new Preflight(PreflightKind.CONFLICT, current, ordered, lines, null, null, null);
            }
        }
        if (!ordered.isEmpty() && ordered.stream().noneMatch(unit -> unit.getStatus() == ApplicationUnitStatus.PENDING)) {
            AmendmentDocumentStatus outcome = outcome(ordered);
            boolean recovered = false;
            if (current.getStatus() == AmendmentDocumentStatus.EXECUTED) {
                recovered = recordOutcomeLocked(current, outcome, command);
                if (!recovered) return new Preflight(PreflightKind.CONFLICT, current, ordered, lines, null, null, null);
            } else if (current.getStatus() != outcome) {
                return new Preflight(PreflightKind.CONFLICT, current, ordered, lines, null, null, null);
            }
            return new Preflight(PreflightKind.REPLAY, current, ordered, lines,
                    new ApplyResult(outcome, true, unitOutcomes(current)), null, recovered ? outcome : null);
        }
        if (allDigestsConflict(current, ordered, lines)) {
            return new Preflight(PreflightKind.ALL_LEDGER_CONFLICT, current, ordered, lines, null, null, null);
        }
        Transition permission = documentService.applyTransition(current.getStatus(), AmendmentDocumentStatus.APPLIED);
        return new Preflight(permission.allowed() ? PreflightKind.PROCEED : PreflightKind.NOT_EXECUTED,
                current, ordered, lines, null, permission, null);
    }

    /** Reject changed or unverifiable committed units before replay or processing pending siblings. */
    private boolean matchesTerminalAttempt(AmendmentDocument document, List<AmendmentApplicationUnit> ordered,
                                           List<ApplyLine> lines, ApplyCommand command) {
        for (AmendmentApplicationUnit unit : ordered) {
            if (unit.getStatus() == ApplicationUnitStatus.PENDING) {
                continue;
            }
            String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (!digest.equals(unit.getApplyKeyDigest()) || !hasAttemptEvidence(document, unit, command)) {
                return false;
            }
            if (unit.getStatus() == ApplicationUnitStatus.APPLIED) {
                Optional<DocumentApplyLedgerEntry> entry = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                        document.getTenantId(), document.getId(), unit.getUnitNo());
                if (entry.isEmpty() || !digest.equals(entry.get().getApplyKeyDigest())) {
                    return false;
                }
            } else if (unit.getStatus() == ApplicationUnitStatus.BLOCKED && !blockedLedgerConsistent(document, unit)) {
                return false;
            }
        }
        return true;
    }

    /** True when every unit already has a ledger row and none of those digests match. */
    private boolean allDigestsConflict(AmendmentDocument document, List<AmendmentApplicationUnit> ordered,
                                       List<ApplyLine> lines) {
        if (ordered.isEmpty()) {
            return false;
        }
        for (AmendmentApplicationUnit unit : ordered) {
            Optional<DocumentApplyLedgerEntry> existing = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                    document.getTenantId(), document.getId(), unit.getUnitNo());
            if (existing.isEmpty()) {
                return false;
            }
            String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (digest.equals(existing.get().getApplyKeyDigest())) {
                return false;
            }
        }
        return true;
    }

    private boolean blockDigestConflict(AmendmentDocument document, AmendmentApplicationUnit stale, ApplyCommand command) {
        lockAndRefresh(document.getId());
        AmendmentDocument fresh = documents.findById(document.getId()).orElseThrow();
        if (!fresh.getTenantId().equals(command.tenantId())
                || fresh.getCurrentRevisionNo() != attemptRevision(document)
                || attemptRevision(fresh) != attemptRevision(document)) {
            throw new DocumentApplyException("apply revision changed", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        document = fresh;
        List<ApplyLine> lines = gateway.lines(document.getId(), document.getCurrentRevisionNo());
        AmendmentApplicationUnit unit = orderedUnits(document).stream()
                .filter(row -> row.getUnitNo().equals(stale.getUnitNo()))
                .findFirst()
                .orElseThrow();
        if (!document.getTenantId().equals(unit.getTenantId())) {
            throw new DocumentApplyException("apply unit tenant changed", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (hasForeignTenantLedger(document, unit)) {
            throw new DocumentApplyException("apply ledger tenant differs", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (unit.getStatus() != ApplicationUnitStatus.PENDING) {
            String current = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (unit.getStatus() == ApplicationUnitStatus.BLOCKED && current.equals(unit.getApplyKeyDigest())
                    && hasAttemptEvidence(document, unit, command) && blockedLedgerConsistent(document, unit)) {
                return false;
            }
            throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (document.getStatus() != AmendmentDocumentStatus.EXECUTED) {
            throw new DocumentApplyException("document is no longer executable", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        Optional<DocumentApplyLedgerEntry> conflicting = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                document.getTenantId(), document.getId(), unit.getUnitNo());
        String attemptedDigest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
        if (conflicting.isEmpty() || attemptedDigest.equals(conflicting.get().getApplyKeyDigest())) {
            throw new DocumentApplyException("ledger conflict is no longer verifiable", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        unit.setStatus(ApplicationUnitStatus.BLOCKED);
        unit.setBlockReason("DIGEST_CONFLICT");
        stampAttempt(document, unit, command);
        unit.setApplyKeyDigest(attemptedDigest);
        units.save(unit);
        events.appendApplyDecision(event(document, command, EVENT_UNIT_BLOCKED, null, null, unit.getUnitNo(),
                "DIGEST_CONFLICT", "apply:DIGEST_CONFLICT:apply key digest does not match the ledger"));
        meters.counter(UNITS_METRIC, "result", "blocked").increment();
        return true;
    }

    private boolean anyApplied(AmendmentDocument document) {
        return orderedUnits(document).stream().anyMatch(unit -> unit.getStatus() == ApplicationUnitStatus.APPLIED);
    }

    private boolean pendingMatchesLedger(AmendmentDocument document, AmendmentApplicationUnit unit, List<ApplyLine> lines) {
        Optional<DocumentApplyLedgerEntry> entry = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                document.getTenantId(), document.getId(), unit.getUnitNo());
        return entry.isPresent() && DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()))
                .equals(entry.get().getApplyKeyDigest());
    }

    private static final class PendingLedgerInconsistency extends DocumentApplyException {
        private PendingLedgerInconsistency() {
            super("ledger exists for a unit that is not applied", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
    }

    private boolean applyUnit(AmendmentDocument document, AmendmentApplicationUnit stale, ApplyCommand command) {
        lockAndRefresh(document.getId());
        AmendmentDocument fresh = documents.findById(document.getId()).orElseThrow();
        if (!fresh.getTenantId().equals(command.tenantId())
                || fresh.getCurrentRevisionNo() != attemptRevision(document)
                || attemptRevision(fresh) != attemptRevision(document)) {
            throw new DocumentApplyException("apply revision changed", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        document = fresh;
        gateway.lockSubscription(stale.getTargetRef());
        AmendmentApplicationUnit unit = orderedUnits(document).stream()
                .filter(row -> row.getUnitNo().equals(stale.getUnitNo()))
                .findFirst()
                .orElseThrow();
        if (!document.getTenantId().equals(unit.getTenantId())) {
            throw new DocumentApplyException("apply unit tenant changed", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (hasForeignTenantLedger(document, unit)) {
            throw new DocumentApplyException("apply ledger tenant differs", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        List<ApplyLine> lines = linesFor(gateway.lines(document.getId(), document.getCurrentRevisionNo()), unit.getUnitNo());
        String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, lines);
        if (unit.getStatus() == ApplicationUnitStatus.BLOCKED) {
            if (!digest.equals(unit.getApplyKeyDigest()) || !hasAttemptEvidence(document, unit, command)
                    || !blockedLedgerConsistent(document, unit)) {
                throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                        HttpStatus.CONFLICT, "DIGEST_CONFLICT");
            }
            return false;
        }
        if (unit.getStatus() == ApplicationUnitStatus.APPLIED && (!digest.equals(unit.getApplyKeyDigest())
                || !hasAttemptEvidence(document, unit, command))) {
            throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (unit.getStatus() == ApplicationUnitStatus.PENDING && document.getStatus() != AmendmentDocumentStatus.EXECUTED) {
            throw new DocumentApplyException("document is no longer executable", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        Optional<DocumentApplyLedgerEntry> existing = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                document.getTenantId(), document.getId(), unit.getUnitNo());
        if (unit.getStatus() == ApplicationUnitStatus.APPLIED && existing.isEmpty()) {
            throw new DocumentApplyException("applied unit has no ledger entry", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (existing.isPresent()) {
            if (!digest.equals(existing.get().getApplyKeyDigest())) {
                throw new DocumentApplyException("apply key digest does not match the ledger",
                        HttpStatus.CONFLICT, "DIGEST_CONFLICT");
            }
            if (unit.getStatus() == ApplicationUnitStatus.PENDING) {
                throw new PendingLedgerInconsistency();
            }
            return false;
        }
        Subscription subscription = gateway.subscription(unit.getTargetRef())
                .flatMap(target -> subscriptions.findByIdForUpdate(target.id()))
                .orElse(null);
        List<SubscriptionRatePeriod> periods = subscription == null ? List.of()
                : rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(subscription.getId());
        String observedPricebook = command.observedPricebookByUnit().get(unit.getUnitNo());
        String block = subscription != null && !gateway.targetBelongsToContract(
                subscription.getId(), document.getContractId(), document.getTenantId())
                ? "TARGET_CONTRACT_MISMATCH"
                : DocumentApplyPeriods.blockReason(unit, subscription, lines, periods, observedPricebook);
        if (block != null) {
            unit.setStatus(ApplicationUnitStatus.BLOCKED);
            unit.setBlockReason(block);
            stampAttempt(document, unit, command);
            unit.setApplyKeyDigest(digest);
            units.save(unit);
            events.appendApplyDecision(event(document, command, EVENT_UNIT_BLOCKED, null, null, unit.getUnitNo(),
                    block, "apply:" + block + ":" + DocumentApplyPeriods.detail(block)));
            meters.counter(UNITS_METRIC, "result", "blocked").increment();
            return true;
        }
        int revisionNo = document.getExecutedRevisionNo() == null ? document.getCurrentRevisionNo() : document.getExecutedRevisionNo();
        var opened = DocumentApplyPeriods.write(subscription, revisionNo, lines, periods, rates, gateway);
        if (assetChanges != null) {
            assetChanges.onUnitApplied(subscription, lines, document.getId(), unit.getUnitNo(),
                    command.correlationId(), opened);
        }
        long nextRevision = Math.addExact(subscription.getCommercialRevision(), 1L);
        subscription.setCommercialRevision(nextRevision);
        subscriptions.save(subscription);
        unit.setStatus(ApplicationUnitStatus.APPLIED);
        stampAttempt(document, unit, command);
        unit.setApplyKeyDigest(digest);
        unit.setAppliedCommercialRevision(nextRevision);
        units.save(unit);
        ledger.save(DocumentApplyLedgerEntry.builder()
                .documentId(document.getId())
                .unitNo(unit.getUnitNo())
                .tenantId(document.getTenantId())
                .applyKeyDigest(digest)
                .createdBy(limit(command.callerId(), 255))
                .build());
        events.appendApplyDecision(event(document, command, EVENT_UNIT_APPLIED, null, null, unit.getUnitNo(),
                null, "apply:unit:" + unit.getUnitNo()));
        meters.counter(UNITS_METRIC, "result", "applied").increment();
        return true;
    }

    private static int attemptRevision(AmendmentDocument document) {
        return document.getExecutedRevisionNo() == null ? document.getCurrentRevisionNo() : document.getExecutedRevisionNo();
    }

    static String pinFingerprint(AmendmentDocument document, AmendmentApplicationUnit unit, ApplyCommand command) {
        String value = command.observedPricebookByUnit().get(unit.getUnitNo());
        String pin = command.observedPricebookByUnit().containsKey(unit.getUnitNo())
                ? "present:" + value.length() + ":" + value : "absent";
        String raw = "v1|" + document.getTenantId() + "|" + document.getId() + "|"
                + attemptRevision(document) + "|" + unit.getUnitNo() + "|" + pin;
        return "v1:" + CommercialDigest.sha256(raw.getBytes(StandardCharsets.UTF_8));
    }

    private void stampAttempt(AmendmentDocument document, AmendmentApplicationUnit unit, ApplyCommand command) {
        unit.setAttemptRevisionNo(attemptRevision(document));
        unit.setAttemptPinFingerprint(pinFingerprint(document, unit, command));
    }

    private boolean hasAttemptEvidence(AmendmentDocument document, AmendmentApplicationUnit unit, ApplyCommand command) {
        return document.getTenantId().equals(unit.getTenantId())
                && java.util.Objects.equals(attemptRevision(document), unit.getAttemptRevisionNo())
                && pinFingerprint(document, unit, command).equals(unit.getAttemptPinFingerprint())
                && (unit.getStatus() != ApplicationUnitStatus.BLOCKED
                    || (unit.getBlockReason() != null && !unit.getBlockReason().isBlank()));
    }

    private boolean blockedLedgerConsistent(AmendmentDocument document, AmendmentApplicationUnit unit) {
        Optional<DocumentApplyLedgerEntry> entry = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                document.getTenantId(), document.getId(), unit.getUnitNo());
        if ("DIGEST_CONFLICT".equals(unit.getBlockReason())) {
            return entry.isPresent() && !unit.getApplyKeyDigest().equals(entry.get().getApplyKeyDigest());
        }
        return entry.isEmpty();
    }

    /** The ledger key omits tenant, so a wrong-tenant row must not be mistaken for absence. */
    private boolean hasForeignTenantLedger(AmendmentDocument document, AmendmentApplicationUnit unit) {
        return ledger.findById(new DocumentApplyLedgerEntry.Key(document.getId(), unit.getUnitNo()))
                .map(entry -> !document.getTenantId().equals(entry.getTenantId())).orElse(false);
    }

    /** Caller already holds the document lock; no context clear may occur after mutations begin. */
    private boolean recordOutcomeLocked(AmendmentDocument fresh, AmendmentDocumentStatus outcome, ApplyCommand command) {
        if (fresh.getStatus() == outcome) {
            return false;
        }
        Transition transition = documentService.applyTransition(fresh.getStatus(), outcome);
        if (!transition.allowed()) {
            return false;
        }
        AmendmentDocumentStatus from = fresh.getStatus();
        fresh.setStatus(outcome);
        fresh.setAppliedAt(Instant.now());
        fresh.setUpdatedBy(limit(command.callerId(), 255));
        documents.save(fresh);
        events.appendApplyDecision(event(fresh, command, documentEvent(outcome), from, outcome, null, null,
                "apply:document:" + outcome.name()));
        return true;
    }

    private void audit(AmendmentDocument document, ApplyCommand command, String reason, String detail) {
        unitTransaction.executeWithoutResult(status ->
                events.appendApplyDecision(event(document, command, EVENT_REFUSED, document.getStatus(), null, null,
                        reason, "apply:" + reason + ":" + detail)));
        meters.counter(UNITS_METRIC, "result", "refused").increment();
        log.info("document apply refused documentId={} reason={} caller={}", document.getId(), reason, command.callerId());
    }

    private AmendmentEventAppend event(AmendmentDocument document, ApplyCommand command, String type,
                                       AmendmentDocumentStatus from, AmendmentDocumentStatus to, Integer unitNo,
                                       String reason, String payloadRef) {
        String authorities = command.authorities().isBlank() ? "NONE" : command.authorities();
        return new AmendmentEventAppend(document.getId(), type, from, to,
                document.getExecutedRevisionNo(), unitNo, limit(command.callerId(), 255), ActorType.SERVICE,
                limit(authorities, 255), Instant.now(), reason,
                CommercialDigest.sha256(type.getBytes(StandardCharsets.UTF_8)), limit(payloadRef, 1024),
                null, limit(command.correlationId(), 255), limit(command.callerId(), 255));
    }

    private List<AmendmentApplicationUnit> orderedUnits(AmendmentDocument document) {
        return units.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(document.getId()).stream()
                .sorted(Comparator.comparing(AmendmentApplicationUnit::getDependencyOrder)
                        .thenComparing(AmendmentApplicationUnit::getUnitNo))
                .toList();
    }

    private static List<ApplyLine> linesFor(List<ApplyLine> lines, int unitNo) {
        return lines.stream().filter(line -> line.unitNo() == unitNo).toList();
    }

    private static AmendmentDocumentStatus outcome(List<AmendmentApplicationUnit> rows) {
        long applied = rows.stream().filter(unit -> unit.getStatus() == ApplicationUnitStatus.APPLIED).count();
        if (applied == rows.size()) {
            return AmendmentDocumentStatus.APPLIED;
        }
        if (applied == 0) {
            return AmendmentDocumentStatus.APPLY_BLOCKED;
        }
        return AmendmentDocumentStatus.PARTIALLY_APPLIED;
    }

    private void count(AmendmentDocumentStatus outcome) {
        if (outcome == AmendmentDocumentStatus.PARTIALLY_APPLIED || outcome == AmendmentDocumentStatus.APPLY_BLOCKED) {
            meters.counter(ALERT_METRIC, "status", outcome.name()).increment();
        }
    }

    private static String documentEvent(AmendmentDocumentStatus outcome) {
        return switch (outcome) {
            case APPLIED -> EVENT_DOCUMENT_APPLIED;
            case PARTIALLY_APPLIED -> EVENT_DOCUMENT_PARTIAL;
            case APPLY_BLOCKED -> EVENT_DOCUMENT_BLOCKED;
            case DRAFT, IN_REVIEW, APPROVED, OUT_FOR_SIGNATURE, REJECTED, EXECUTED, WITHDRAWN, VOID ->
                    throw new IllegalStateException("apply outcome is not a document result");
        };
    }

    private static ResourceNotFoundException unknown(UUID id) {
        return new ResourceNotFoundException("Amendment", id.toString());
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
