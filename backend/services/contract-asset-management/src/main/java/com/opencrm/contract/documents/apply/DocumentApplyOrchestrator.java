package com.opencrm.contract.documents.apply;

import com.opencrm.common.commercial.CommercialDigest;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.documents.AmendmentDocumentService;
import com.opencrm.contract.documents.Transition;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                                     PlatformTransactionManager transactions) {
        this.documents = documents;
        this.units = units;
        this.ledger = ledger;
        this.subscriptions = subscriptions;
        this.rates = rates;
        this.events = events;
        this.documentService = documentService;
        this.gateway = gateway;
        this.meters = meters;
        this.unitTransaction = new TransactionTemplate(transactions);
        this.unitTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        List<AmendmentApplicationUnit> ordered = orderedUnits(document);
        List<ApplyLine> lines = gateway.lines(document.getId(), document.getCurrentRevisionNo());
        if (!matchesTerminalAttempt(document, ordered, lines)) {
            audit(document, command, "DIGEST_CONFLICT", "apply key digest does not match the recorded attempt");
            throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        boolean inconsistentPending = Boolean.TRUE.equals(unitTransaction.execute(status -> {
            gateway.lockDocument(document.getId());
            for (AmendmentApplicationUnit unit : orderedUnits(document)) {
                if (unit.getStatus() == ApplicationUnitStatus.PENDING && pendingMatchesLedger(document, unit, lines)) {
                    return true;
                }
            }
            return false;
        }));
        if (inconsistentPending) {
            audit(document, command, "DIGEST_CONFLICT", "ledger exists for a unit that is not applied");
            throw new DocumentApplyException("ledger exists for a unit that is not applied",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        if (sameDigestReplay(document, ordered, lines)) {
            if (document.getStatus() == AmendmentDocumentStatus.EXECUTED) {
                if (ordered.stream().anyMatch(unit -> unit.getStatus() != ApplicationUnitStatus.APPLIED)) {
                    audit(document, command, "DIGEST_CONFLICT", "ledger exists for a unit that is not applied");
                    throw new DocumentApplyException("ledger exists for a unit that is not applied",
                            HttpStatus.CONFLICT, "DIGEST_CONFLICT");
                }
                // All unit transactions committed before the outcome transaction could run.
                // The document update and its audit event share the same locked transaction.
                unitTransaction.executeWithoutResult(status ->
                        recordOutcome(document.getId(), AmendmentDocumentStatus.APPLIED, command));
                AmendmentDocument current = documents.findById(document.getId()).orElseThrow();
                if (current.getStatus() != AmendmentDocumentStatus.APPLIED) {
                    throw new DocumentApplyException("could not finalize the applied document",
                            HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
                }
                return new ApplyResult(AmendmentDocumentStatus.APPLIED, true);
            }
            log.info("document apply replay documentId={} status={}", document.getId(), document.getStatus());
            return new ApplyResult(document.getStatus(), true);
        }
        if (recordedDigestConflictReplay(document, ordered, lines)) {
            log.info("document apply replay documentId={} status={}", document.getId(), document.getStatus());
            return new ApplyResult(document.getStatus(), true, unitOutcomes(document, Map.of(), lines));
        }
        if (allDigestsConflict(document, ordered, lines)) {
            unitTransaction.executeWithoutResult(status -> {
                for (AmendmentApplicationUnit unit : ordered) {
                    events.appendApplyDecision(event(document, command, EVENT_UNIT_BLOCKED, null, null,
                            unit.getUnitNo(), "DIGEST_CONFLICT",
                            "apply:DIGEST_CONFLICT:apply key digest does not match the ledger"));
                }
            });
            throw new DocumentApplyException("apply key digest does not match the ledger", HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        Transition permission = documentService.applyTransition(document.getStatus(), AmendmentDocumentStatus.APPLIED);
        if (!permission.allowed()) {
            audit(document, command, "NOT_EXECUTED", "apply is refused unless the document is EXECUTED");
            throw new DocumentApplyException("apply is refused unless the document is EXECUTED",
                    HttpStatus.valueOf(permission.httpStatus()), permission.errorCode());
        }

        boolean changed = false;
        Map<Integer, String> blockReasons = new LinkedHashMap<>();
        for (AmendmentApplicationUnit unit : ordered) {
            try {
                changed |= Boolean.TRUE.equals(unitTransaction.execute(
                        status -> applyUnit(document, unit, lines, command, blockReasons)));
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
                blockReasons.put(unit.getUnitNo(), "DIGEST_CONFLICT");
                try {
                    changed |= Boolean.TRUE.equals(unitTransaction.execute(
                            status -> blockDigestConflict(document, unit, lines, command)));
                } catch (DocumentApplyException terminalConflict) {
                    audit(document, command, "DIGEST_CONFLICT", "apply key digest does not match the recorded attempt");
                    throw terminalConflict;
                }
            }
        }
        AmendmentDocumentStatus outcome = outcome(orderedUnits(document));
        AmendmentDocument current = documents.findById(document.getId()).orElseThrow();
        boolean alreadyRecorded = current.getStatus() == outcome;
        if (!alreadyRecorded) {
            unitTransaction.executeWithoutResult(status -> recordOutcome(document.getId(), outcome, command));
            count(outcome);
            log.info("document apply documentId={} status={} alert={}", document.getId(), outcome, ALERT_CONDITION);
        }
        return new ApplyResult(outcome, alreadyRecorded && !changed, unitOutcomes(document, blockReasons, lines));
    }

    /**
     * Present when at least one unit is BLOCKED. Applied siblings are included.
     * BLOCKED is the persisted form of the OQ5 APPLY_BLOCKED unit state.
     */
    private List<ApplyResult.UnitOutcome> unitOutcomes(AmendmentDocument document, Map<Integer, String> blockReasons,
                                                        List<ApplyLine> lines) {
        List<ApplyResult.UnitOutcome> rows = new ArrayList<>();
        boolean anyBlocked = false;
        for (AmendmentApplicationUnit unit : orderedUnits(document)) {
            if (unit.getStatus() == ApplicationUnitStatus.APPLIED) {
                rows.add(new ApplyResult.UnitOutcome(unit.getUnitNo(), ApplicationUnitStatus.APPLIED.name(), null));
            } else if (unit.getStatus() == ApplicationUnitStatus.BLOCKED) {
                anyBlocked = true;
                String reason = blockReasons.get(unit.getUnitNo());
                if (reason == null && unitDigestConflicts(document, unit, lines)) {
                    reason = "DIGEST_CONFLICT";
                }
                rows.add(new ApplyResult.UnitOutcome(unit.getUnitNo(), ApplicationUnitStatus.BLOCKED.name(), reason));
            }
        }
        return anyBlocked ? rows : null;
    }

    private boolean unitDigestConflicts(AmendmentDocument document, AmendmentApplicationUnit unit, List<ApplyLine> lines) {
        Optional<DocumentApplyLedgerEntry> existing = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                document.getTenantId(), document.getId(), unit.getUnitNo());
        if (existing.isEmpty()) {
            return false;
        }
        String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
        return !digest.equals(existing.get().getApplyKeyDigest());
    }

    /**
     * The conflicting unit is already terminal and the document outcome is recorded.
     * A second call must not append another block row or refuse with APPLY_REFUSED.
     */
    private boolean recordedDigestConflictReplay(AmendmentDocument document, List<AmendmentApplicationUnit> ordered,
                                                  List<ApplyLine> lines) {
        if (ordered.isEmpty() || !conflictingDigest(document, ordered, lines)) {
            return false;
        }
        AmendmentDocumentStatus recorded = outcome(ordered);
        if (document.getStatus() != recorded) {
            return false;
        }
        if (recorded != AmendmentDocumentStatus.PARTIALLY_APPLIED && recorded != AmendmentDocumentStatus.APPLY_BLOCKED) {
            return false;
        }
        return ordered.stream().noneMatch(unit -> unit.getStatus() == ApplicationUnitStatus.PENDING);
    }

    /** Reject changed or unverifiable committed units before replay or processing pending siblings. */
    private boolean matchesTerminalAttempt(AmendmentDocument document, List<AmendmentApplicationUnit> ordered,
                                           List<ApplyLine> lines) {
        for (AmendmentApplicationUnit unit : ordered) {
            if (unit.getStatus() == ApplicationUnitStatus.PENDING) {
                continue;
            }
            String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (!digest.equals(unit.getApplyKeyDigest())) {
                return false;
            }
            if (unit.getStatus() == ApplicationUnitStatus.APPLIED) {
                Optional<DocumentApplyLedgerEntry> entry = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                        document.getTenantId(), document.getId(), unit.getUnitNo());
                if (entry.isEmpty() || !digest.equals(entry.get().getApplyKeyDigest())) {
                    return false;
                }
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

    private boolean blockDigestConflict(AmendmentDocument document, AmendmentApplicationUnit stale,
                                        List<ApplyLine> lines, ApplyCommand command) {
        gateway.lockDocument(document.getId());
        AmendmentApplicationUnit unit = orderedUnits(document).stream()
                .filter(row -> row.getUnitNo().equals(stale.getUnitNo()))
                .findFirst()
                .orElseThrow();
        if (unit.getStatus() != ApplicationUnitStatus.PENDING) {
            String current = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (unit.getStatus() == ApplicationUnitStatus.BLOCKED && current.equals(unit.getApplyKeyDigest())) {
                return false;
            }
            throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
        }
        unit.setStatus(ApplicationUnitStatus.BLOCKED);
        unit.setApplyKeyDigest(DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo())));
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

    private boolean applyUnit(AmendmentDocument document, AmendmentApplicationUnit stale, List<ApplyLine> allLines,
                              ApplyCommand command, Map<Integer, String> blockReasons) {
        gateway.lockDocument(document.getId());
        gateway.lockSubscription(stale.getTargetRef());
        AmendmentApplicationUnit unit = orderedUnits(document).stream()
                .filter(row -> row.getUnitNo().equals(stale.getUnitNo()))
                .findFirst()
                .orElseThrow();
        List<ApplyLine> lines = linesFor(allLines, unit.getUnitNo());
        String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, lines);
        if (unit.getStatus() == ApplicationUnitStatus.BLOCKED) {
            if (!digest.equals(unit.getApplyKeyDigest())) {
                throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                        HttpStatus.CONFLICT, "DIGEST_CONFLICT");
            }
            return false;
        }
        if (unit.getStatus() == ApplicationUnitStatus.APPLIED && !digest.equals(unit.getApplyKeyDigest())) {
            throw new DocumentApplyException("apply key digest does not match the recorded attempt",
                    HttpStatus.CONFLICT, "DIGEST_CONFLICT");
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
        String block = DocumentApplyPeriods.blockReason(unit, subscription, lines, periods, observedPricebook);
        if (block != null) {
            unit.setStatus(ApplicationUnitStatus.BLOCKED);
            unit.setApplyKeyDigest(digest);
            units.save(unit);
            blockReasons.put(unit.getUnitNo(), block);
            events.appendApplyDecision(event(document, command, EVENT_UNIT_BLOCKED, null, null, unit.getUnitNo(),
                    block, "apply:" + block + ":" + DocumentApplyPeriods.detail(block)));
            meters.counter(UNITS_METRIC, "result", "blocked").increment();
            return true;
        }
        int revisionNo = document.getExecutedRevisionNo() == null ? document.getCurrentRevisionNo() : document.getExecutedRevisionNo();
        DocumentApplyPeriods.write(subscription, revisionNo, lines, periods, rates, gateway);
        long nextRevision = Math.addExact(subscription.getCommercialRevision(), 1L);
        subscription.setCommercialRevision(nextRevision);
        subscriptions.save(subscription);
        unit.setStatus(ApplicationUnitStatus.APPLIED);
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

    private void recordOutcome(UUID documentId, AmendmentDocumentStatus outcome, ApplyCommand command) {
        gateway.lockDocument(documentId);
        AmendmentDocument fresh = documents.findById(documentId).orElseThrow();
        if (fresh.getStatus() == outcome) {
            return;
        }
        Transition transition = documentService.applyTransition(fresh.getStatus(), outcome);
        if (!transition.allowed()) {
            return;
        }
        AmendmentDocumentStatus from = fresh.getStatus();
        fresh.setStatus(outcome);
        fresh.setAppliedAt(Instant.now());
        fresh.setUpdatedBy(limit(command.callerId(), 255));
        documents.save(fresh);
        events.appendApplyDecision(event(fresh, command, documentEvent(outcome), from, outcome, null, null,
                "apply:document:" + outcome.name()));
    }

    private boolean conflictingDigest(AmendmentDocument document, List<AmendmentApplicationUnit> ordered, List<ApplyLine> lines) {
        for (AmendmentApplicationUnit unit : ordered) {
            Optional<DocumentApplyLedgerEntry> existing = ledger.findByTenantIdAndDocumentIdAndUnitNo(
                    document.getTenantId(), document.getId(), unit.getUnitNo());
            if (existing.isEmpty()) {
                continue;
            }
            String digest = DocumentApplyDigest.unitDigest(document.getId(), unit, linesFor(lines, unit.getUnitNo()));
            if (!digest.equals(existing.get().getApplyKeyDigest())) {
                return true;
            }
        }
        return false;
    }

    private boolean sameDigestReplay(AmendmentDocument document, List<AmendmentApplicationUnit> ordered, List<ApplyLine> lines) {
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
            if (!digest.equals(existing.get().getApplyKeyDigest())) {
                return false;
            }
        }
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
