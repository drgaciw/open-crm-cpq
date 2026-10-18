package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.RenewalPolicyRecord;
import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class RenewalPolicyConcurrencyTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void replayOfTheSameBodyIsIdempotentAndADifferentBodyAppendsWithoutMutatingThePriorVersion() {
        Store store = new Store(null);
        RenewalPolicyRecordService service = service(store);
        RenewalPolicyDraft same = draft("SAME", null);
        RenewalPolicyRecord first = service.append(TENANT, "service:quote-service", same);
        RenewalPolicyRecord replay = service.append(TENANT, "service:quote-service", same);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getPolicyVersion()).isEqualTo(1);
        assertThat(store.rows).hasSize(1);

        RenewalPolicyRecord second = service.append(TENANT, "service:quote-service", draft("UPLIFT_PCT", java.math.BigDecimal.ONE));
        assertThat(second.getPolicyVersion()).isEqualTo(2);
        assertThat(first.getMode()).isEqualTo("SAME");
        assertThat(first.getPolicyVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.append(TENANT, "service:quote-service",
                new RenewalPolicyDraft(RenewalPolicyScopeKind.ACCOUNT, ACCOUNT, "SAME", null, null, null, null, null,
                        null, null, null, "different-marker", LocalDate.of(2026, 1, 1), null, 2)))
                .isInstanceOf(RenewalPolicyConflictException.class);
    }

    @Test
    void concurrentIdenticalWritesHaveOneWinnerAndConcurrentConflictsDoNotDoubleCommit() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Store store = new Store(barrier);
        RenewalPolicyRecordService service = service(store);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RenewalPolicyRecord> left = pool.submit(() -> service.append(TENANT, "service:quote-service", draft("SAME", null)));
            Future<RenewalPolicyRecord> right = pool.submit(() -> service.append(TENANT, "service:quote-service", draft("SAME", null)));
            assertThat(left.get(5, TimeUnit.SECONDS).getPolicyVersion()).isEqualTo(1);
            assertThat(right.get(5, TimeUnit.SECONDS).getId()).isEqualTo(left.get().getId());
            assertThat(store.rows).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        CyclicBarrier conflictGate = new CyclicBarrier(2);
        Store conflicts = new Store(conflictGate);
        RenewalPolicyRecordService racing = service(conflicts);
        ExecutorService racers = Executors.newFixedThreadPool(2);
        try {
            Future<Object> same = racers.submit(() -> serviceCall(racing, draft("SAME", null)));
            Future<Object> other = racers.submit(() -> serviceCall(racing, draft("UPLIFT_PCT", java.math.BigDecimal.TEN)));
            List<Object> outcomes = List.of(same.get(5, TimeUnit.SECONDS), other.get(5, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(RenewalPolicyRecord.class::isInstance)).hasSize(1);
            assertThat(outcomes.stream().filter(RenewalPolicyConflictException.class::isInstance)).hasSize(1);
            assertThat(conflicts.rows).hasSize(1);
        } finally {
            racers.shutdownNow();
        }
    }

    private static Object serviceCall(RenewalPolicyRecordService service, RenewalPolicyDraft draft) {
        try {
            return service.append(TENANT, "service:quote-service", draft);
        } catch (RenewalPolicyConflictException ex) {
            return ex;
        }
    }

    private static RenewalPolicyRecordService service(Store store) {
        return new RenewalPolicyRecordService(store, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static RenewalPolicyDraft draft(String mode, java.math.BigDecimal percent) {
        return new RenewalPolicyDraft(RenewalPolicyScopeKind.ACCOUNT, ACCOUNT, mode, null, percent, null, null, null,
                null, null, null, null, LocalDate.of(2026, 1, 1), null, null);
    }

    private static final class Store implements RenewalPolicyRecords {
        private final List<RenewalPolicyRecord> rows = new ArrayList<>();
        private final CyclicBarrier findBarrier;
        private final AtomicInteger finds = new AtomicInteger();

        private Store(CyclicBarrier findBarrier) {
            this.findBarrier = findBarrier;
        }

        @Override
        public List<RenewalPolicyRecord> findScoped(UUID tenantId, RenewalPolicyScopeKind scopeKind, UUID scopeRef) {
            List<RenewalPolicyRecord> snapshot;
            synchronized (rows) {
                snapshot = rows.stream()
                        .filter(row -> tenantId.equals(row.getTenantId()))
                        .filter(row -> row.getScopeKind() == scopeKind && scopeRef.equals(row.getScopeRef()))
                        .toList();
            }
            if (findBarrier != null && finds.incrementAndGet() <= 2) {
                try {
                    findBarrier.await(2, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
            return snapshot;
        }

        @Override
        public RenewalPolicyRecord insert(RenewalPolicyRecord record) {
            synchronized (rows) {
                boolean duplicate = rows.stream().anyMatch(row -> row.getTenantId().equals(record.getTenantId())
                        && row.getScopeKind() == record.getScopeKind()
                        && row.getScopeRef().equals(record.getScopeRef())
                        && row.getPolicyVersion().equals(record.getPolicyVersion()));
                if (duplicate) {
                    throw new RenewalPolicyVersionConflictException("duplicate version");
                }
                if (record.getId() == null) {
                    record.setId(UUID.randomUUID());
                }
                rows.add(record);
                return record;
            }
        }
    }
}
