package com.opencrm.contract.documents.apply;

import com.opencrm.contract.documents.AmendmentDocumentService;
import com.opencrm.contract.documents.AmendmentStateMachine;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.ApplyTargetType;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.documents.AmendmentApplicationUnitRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.DocumentApplyLedgerRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * In-memory CAM apply graph. The advisory lock is held until the unit transaction completes,
 * which is the same lifetime as {@code pg_advisory_xact_lock}.
 */
final class ApplyFixture {

    final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    final AmendmentDocument document;
    final List<AmendmentApplicationUnit> units = new ArrayList<>();
    final List<ApplyLine> lines = new ArrayList<>();
    final List<SubscriptionRatePeriod> periods = new ArrayList<>();
    final ConcurrentHashMap<String, DocumentApplyLedgerEntry> ledger = new ConcurrentHashMap<>();
    final List<AmendmentEventAppend> events = new ArrayList<>();
    final List<LineBind> binds = new ArrayList<>();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final DocumentApplyGateway gateway;
    final DocumentApplyOrchestrator orchestrator;
    final AssetLifecycleReadiness readiness;
    private final AtomicLong periodIds = new AtomicLong(100);
    private final ReentrantLock documentLock = new ReentrantLock();
    private final java.util.concurrent.atomic.AtomicInteger lockCalls = new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.IntConsumer afterDocumentLock = ignored -> {};
    java.util.function.IntConsumer afterDocumentUnlock = ignored -> {};

    ApplyFixture() {
        this(true);
    }

    ApplyFixture(boolean assetLifecycleReady) {
        document = AmendmentDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .contractId(UUID.randomUUID())
                .status(AmendmentDocumentStatus.EXECUTED)
                .currentRevisionNo(1)
                .executedRevisionNo(1)
                .createdBy("document-creator")
                .build();

        AmendmentDocumentRepository documents = mock(AmendmentDocumentRepository.class);
        AmendmentApplicationUnitRepository unitRepository = mock(AmendmentApplicationUnitRepository.class);
        DocumentApplyLedgerRepository ledgerRepository = mock(DocumentApplyLedgerRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        SubscriptionRatePeriodRepository rates = mock(SubscriptionRatePeriodRepository.class);
        AmendmentEventWriter writer = mock(AmendmentEventWriter.class);
        AmendmentDocumentService documentService = mock(AmendmentDocumentService.class);
        gateway = mock(DocumentApplyGateway.class);
        when(gateway.targetBelongsToContract(anyLong(), any(), any())).thenReturn(true);
        when(gateway.contractTenantBlock(anyLong())).thenReturn(null);

        when(documents.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return document.getId().equals(id) ? Optional.of(document) : Optional.empty();
        });
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(any())).thenAnswer(invocation ->
                units.stream()
                        .sorted(Comparator.comparing(AmendmentApplicationUnit::getDependencyOrder)
                                .thenComparing(AmendmentApplicationUnit::getUnitNo))
                        .toList());
        when(unitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.findById(any())).thenAnswer(invocation -> {
            DocumentApplyLedgerEntry.Key key = invocation.getArgument(0);
            return Optional.ofNullable(ledger.get(key.getDocumentId() + ":" + key.getUnitNo()));
        });
        when(ledgerRepository.findByTenantIdAndDocumentIdAndUnitNo(any(), any(), any())).thenAnswer(invocation -> {
            UUID requestedTenant = invocation.getArgument(0);
            UUID documentId = invocation.getArgument(1);
            Integer unitNo = invocation.getArgument(2);
            return Optional.ofNullable(ledger.get(documentId + ":" + unitNo))
                    .filter(entry -> requestedTenant.equals(entry.getTenantId()));
        });
        when(ledgerRepository.save(any())).thenAnswer(invocation -> {
            DocumentApplyLedgerEntry entry = invocation.getArgument(0);
            ledger.put(entry.getDocumentId() + ":" + entry.getUnitNo(), entry);
            return entry;
        });
        when(subscriptions.findByIdForUpdate(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return periodsView().stream()
                    .map(SubscriptionRatePeriod::getSubscriptionId)
                    .filter(id::equals)
                    .findFirst()
                    .map(ignored -> subscription(id))
                    .or(() -> Optional.ofNullable(subscription(id)));
        });
        when(subscriptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(any()))
                .thenAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    return periods.stream().filter(period -> id.equals(period.getSubscriptionId())).toList();
                });
        Answer<SubscriptionRatePeriod> persist = invocation -> {
            SubscriptionRatePeriod period = invocation.getArgument(0);
            if (period.getId() == null) {
                period.setId(periodIds.incrementAndGet());
            }
            if (periods.stream().noneMatch(existing -> existing == period)) {
                periods.add(period);
            }
            return period;
        };
        when(rates.save(any())).thenAnswer(persist);
        when(rates.saveAndFlush(any())).thenAnswer(persist);
        when(writer.appendApplyDecision(any())).thenAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        });
        when(documentService.applyTransition(any(), any())).thenAnswer(invocation ->
                AmendmentStateMachine.apply(invocation.getArgument(0), invocation.getArgument(1)));
        when(gateway.lines(any(), anyInt())).thenAnswer(invocation -> List.copyOf(lines));
        when(gateway.subscription(any())).thenAnswer(invocation -> {
            String uuid = invocation.getArgument(0);
            Subscription match = subscriptionByUuid(uuid);
            if (match == null) {
                return Optional.empty();
            }
            return Optional.of(new ApplyTarget(match.getId(), match.getUuid(), match.getCommercialRevision()));
        });
        doAnswer(invocation -> {
            binds.add(new LineBind(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            return null;
        }).when(gateway).bindAmendmentLine(anyLong(), any(), anyInt());
        doAnswer(invocation -> {
            documentLock.lock();
            int call = lockCalls.incrementAndGet();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    documentLock.unlock();
                    afterDocumentUnlock.accept(call);
                }
            });
            afterDocumentLock.accept(call);
            return null;
        }).when(gateway).lockDocument(any());

        readiness = mock(AssetLifecycleReadiness.class);
        when(readiness.isReady()).thenReturn(assetLifecycleReady);
        orchestrator = new DocumentApplyOrchestrator(documents, unitRepository, ledgerRepository, subscriptions, rates,
                writer, documentService, gateway, meters, new DirectTransactions(), mock(jakarta.persistence.EntityManager.class), readiness);
    }

    AmendmentApplicationUnit unit(int unitNo, Subscription subscription, long observedRevision) {
        AmendmentApplicationUnit row = AmendmentApplicationUnit.builder()
                .documentId(document.getId())
                .unitNo(unitNo)
                .tenantId(tenantId)
                .dependencyOrder(unitNo)
                .targetType(ApplyTargetType.SUBSCRIPTION)
                .targetRef(subscription.getUuid())
                .observedCommercialRevision(observedRevision)
                .status(ApplicationUnitStatus.PENDING)
                .createdBy("document-creator")
                .build();
        units.add(row);
        remembered(subscription);
        return row;
    }

    ApplyLine line(int unitNo, com.opencrm.contract.domain.documents.ActionCode action, String sourceLine,
                   BigDecimal endQty, LocalDate from, LocalDate to, String pricebook) {
        ApplyLine row = new ApplyLine(UUID.randomUUID(), lines.size() + 1, unitNo, action, sourceLine, endQty, from, to,
                pricebook, "product-" + unitNo, new BigDecimal("10.0000"));
        lines.add(row);
        return row;
    }

    Subscription subscription(long id, long revision, LocalDate start, LocalDate end) {
        Subscription row = Subscription.builder()
                .id(id)
                .uuid(UUID.randomUUID().toString())
                .subscriptionNumber("SUB-" + id)
                .customerId(tenantId.toString())
                .quoteId("Q-" + id)
                .status(SubscriptionStatus.ACTIVE)
                .planName("Plan")
                .startDate(start)
                .endDate(end)
                .commercialRevision(revision)
                .build();
        remembered(row);
        return row;
    }

    SubscriptionRatePeriod openPeriod(Subscription subscription, String acceptedLine, BigDecimal quantity,
                                      LocalDate start, LocalDate end) {
        SubscriptionRatePeriod period = SubscriptionRatePeriod.builder()
                .id(periodIds.incrementAndGet())
                .subscriptionId(subscription.getId())
                .acceptedLineUuid(acceptedLine)
                .sequence(1)
                .startDate(start)
                .billingAnchorDate(start)
                .endDate(end)
                .effectiveEndDate(end)
                .quantity(quantity)
                .netUnitPrice(new BigDecimal("10.0000"))
                .netMonthly(new BigDecimal("1000.00"))
                .provenance("ACCEPTED")
                .applied(true)
                .retired(false)
                .build();
        periods.add(period);
        return period;
    }

    /** Sold end stays on {@code endDate}. The effective end is null, which is open-ended coverage. */
    SubscriptionRatePeriod openEndedPeriod(Subscription subscription, String acceptedLine, BigDecimal quantity,
                                           LocalDate start, LocalDate end) {
        SubscriptionRatePeriod period = openPeriod(subscription, acceptedLine, quantity, start, end);
        period.setEffectiveEndDate(null);
        return period;
    }

    ApplyCommand command() {
        return new ApplyCommand(document.getId(), tenantId, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-s4", java.util.Map.of());
    }

    ApplyResult apply() {
        return orchestrator.apply(command());
    }

    long eventsOfType(String eventType) {
        return events.stream().filter(event -> eventType.equals(event.eventType())).count();
    }

    private final List<Subscription> subscriptionRows = new ArrayList<>();

    private void remembered(Subscription subscription) {
        if (subscriptionRows.stream().noneMatch(existing -> existing.getId().equals(subscription.getId()))) {
            subscriptionRows.add(subscription);
        }
    }

    private Subscription subscription(Long id) {
        return subscriptionRows.stream().filter(row -> row.getId().equals(id)).findFirst().orElse(null);
    }

    private Subscription subscriptionByUuid(String uuid) {
        return subscriptionRows.stream().filter(row -> uuid.equals(row.getUuid())).findFirst().orElse(null);
    }

    private List<SubscriptionRatePeriod> periodsView() {
        return periods;
    }

    record LineBind(long periodId, UUID lineId, int revisionNo) {
    }

    private static final class DirectTransactions extends AbstractPlatformTransactionManager {
        private DirectTransactions() {
            setTransactionSynchronization(SYNCHRONIZATION_ALWAYS);
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
