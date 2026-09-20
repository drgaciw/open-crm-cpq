package com.opencrm.ai.selection.refresh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.policy.FilePolicyRepository;
import com.opencrm.ai.selection.policy.PolicyEligibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.opencrm.ai.selection.refresh.SelectionRefreshCoordinator.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionRefreshCoordinatorTest {
    @TempDir Path directory;
    private final MutableClock clock = new MutableClock();
    private final TaskProfile profile = new TaskProfile("advice", "v1", Set.of("response_format"), 100, 100,
            d("0.8"), d("0.9"), 1000, d("1"), Set.of("allowed"), 2, Duration.ofHours(2), Duration.ofHours(1));
    private final Endpoint oldEndpoint = endpoint("old", "0.001");
    private final Endpoint newEndpoint = endpoint("new", "0.001");
    private final AtomicReference<CatalogSnapshot> catalog = new AtomicReference<>();
    private final AtomicReference<SelectionReport> next = new AtomicReference<>();
    private final List<Outcome> outcomes = new ArrayList<>();
    private FilePolicyRepository repository;

    @BeforeEach void setup() throws Exception {
        repository = new FilePolicyRepository(directory, new ObjectMapper().findAndRegisterModules());
        catalog.set(new CatalogSnapshot("catalog1", clock.instant().minusSeconds(1), clock.instant().plusSeconds(7200),
                List.of(oldEndpoint, newEndpoint), "hash"));
        next.set(report(policy("new1", newEndpoint, "0.4", clock.instant())));
    }

    @Test void defaultSchedulingAndShadowCannotActivateOrCreateWorker() throws Exception {
        AtomicInteger evaluations = new AtomicInteger();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        try (var coordinator = coordinator(Settings.defaults(), () -> { evaluations.incrementAndGet(); return next.get(); }, () -> scheduler)) {
            assertThat(coordinator.start()).isFalse();
            assertThat(evaluations).hasValue(0);
            assertThat(coordinator.runOnce().reason()).isEqualTo("SHADOW_PROPOSAL");
            assertThat(repository.active(profile.id())).isEmpty();
            assertThat(repository.read("new1").routes().getFirst().endpoint()).isEqualTo(newEndpoint);
        }
        verifyNoInteractions(scheduler);
    }

    @Test void configuredSingleWorkerRepeatsChecksAndClosesWithoutSleeping() throws Exception {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        AtomicInteger evaluations = new AtomicInteger();
        Settings settings = new Settings(true, Mode.SHADOW, Duration.ofSeconds(30), d("0.1"), Duration.ofMinutes(10));
        var coordinator = coordinator(settings, () -> {
            if (evaluations.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
            return next.get();
        }, () -> scheduler);
        assertThat(coordinator.start()).isTrue();
        assertThat(coordinator.start()).isFalse();
        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(action.capture(), eq(0L), eq(30000L), eq(TimeUnit.MILLISECONDS));
        action.getValue().run();
        clock.advance(Duration.ofSeconds(30));
        action.getValue().run();
        assertThat(evaluations).hasValue(2);
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0).reason()).isEqualTo("UNAVAILABLE_EVALUATOR_UNAVAILABLE");
        assertThat(outcomes.get(1).reason()).isEqualTo("SHADOW_PROPOSAL");
        assertThat(outcomes.get(1).checkedAt()).isEqualTo(outcomes.get(0).checkedAt().plusSeconds(30));
        coordinator.close();
        verify(scheduler).shutdownNow();
        assertThatThrownBy(coordinator::runOnce).isInstanceOf(IllegalStateException.class);
    }

    @Test void failedEvaluationRetainsEligiblePolicyButCannotReviveExpiredOrRevokedOne() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        try (var coordinator = coordinator(canary("0.1", Duration.ZERO), () -> { throw new IllegalStateException("offline"); })) {
            assertThat(coordinator.runOnce().reason()).isEqualTo("RETAINED_EVALUATOR_UNAVAILABLE");
            assertThat(repository.active(profile.id())).isPresent();
            clock.advance(Duration.ofHours(1));
            assertThat(coordinator.runOnce().reason()).isEqualTo("UNAVAILABLE_EVALUATOR_UNAVAILABLE");
            assertThat(repository.revoked("old1")).isTrue();
            next.set(report(policy("old1", oldEndpoint, "0.4", clock.instant())));
        }
        try (var coordinator = coordinator(canary("0.1", Duration.ZERO), next::get)) {
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.active(profile.id())).isEmpty();
        }
    }

    @Test void catalogFailureUsesOnlyStillFreshCachedEvidenceAndNeverActivatesProposal() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        AtomicInteger catalogReads = new AtomicInteger();
        Supplier<CatalogSnapshot> evidence = () -> {
            if (catalogReads.getAndIncrement() > 1) throw new IllegalStateException("catalog unavailable");
            return catalog.get();
        };
        try (var coordinator = new SelectionRefreshCoordinator(canary("0.5", Duration.ZERO), repository, () -> profile,
                evidence, next::get, clock, outcomes::add)) {
            assertThat(coordinator.runOnce().reason()).isEqualTo("MINIMUM_BENEFIT_NOT_MET");
            assertThat(coordinator.runOnce().reason()).isEqualTo("RETAINED_EVIDENCE_UNAVAILABLE");
            assertThat(coordinator.lastOutcome().orElseThrow().usedCachedEvidence()).isTrue();
            clock.advance(Duration.ofHours(3));
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.active(profile.id())).isEmpty();
        }
    }

    @Test void switchIntervalAndMinimumBenefitGateOnlyEligibleCurrentPolicies() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        next.set(report(policy("new-small", newEndpoint, "0.48", clock.instant())));
        try (var coordinator = coordinator(canary("0.1", Duration.ofMinutes(10)), next::get)) {
            assertThat(coordinator.runOnce().reason()).isEqualTo("MINIMUM_SWITCH_INTERVAL");
            clock.advance(Duration.ofMinutes(10));
            assertThat(coordinator.runOnce().reason()).isEqualTo("MINIMUM_BENEFIT_NOT_MET");
            next.set(report(policy("new-large", newEndpoint, "0.4", clock.instant())));
            assertThat(coordinator.runOnce().reason()).isEqualTo("ACTIVATED");
            SelectionPolicy active = repository.active(profile.id()).orElseThrow();
            assertThat(active.version()).isEqualTo("new-large");
            assertThat(active.rollbackVersion()).isEqualTo("old1");
            repository.rollback(profile.id(), p -> PolicyEligibility.valid(p, profile, catalog.get(), clock.instant()));
            assertThat(repository.active(profile.id()).orElseThrow().version()).isEqualTo("old1");
        }
    }

    @Test void conflictingRollbackTargetIsRejectedBeforeSavingOrActivating() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        SelectionPolicy candidate = next.get().proposedPolicy();
        next.set(report(new SelectionPolicy(candidate.version(), candidate.profileId(), candidate.profileVersion(),
                candidate.catalogVersion(), candidate.routes(), candidate.effectiveAt(), candidate.expiresAt(), "unrelated-policy")));
        try (var coordinator = coordinator(canary("0", Duration.ZERO), next::get)) {
            assertThat(coordinator.runOnce().reason()).isEqualTo("CONFLICTING_ROLLBACK_VERSION");
            assertThat(repository.active(profile.id()).orElseThrow().version()).isEqualTo("old1");
            assertThatThrownBy(() -> repository.read(candidate.version())).isInstanceOf(java.io.IOException.class);
        }
    }

    @Test void priceCapabilityAndQualityInvalidationOverrideHysteresisInCanary() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        Endpoint increased = endpoint("old", "0.02");
        catalog.set(new CatalogSnapshot("catalog2", clock.instant(), clock.instant().plusSeconds(7200),
                List.of(increased, newEndpoint), "newhash"));
        next.set(report(policy("replacement", newEndpoint, "0.49", clock.instant())));
        try (var coordinator = coordinator(canary("0.9", Duration.ofHours(10)), next::get)) {
            assertThat(coordinator.runOnce().reason()).isEqualTo("ACTIVATED");
            assertThat(repository.revoked("old1")).isTrue();
            SelectionPolicy active = repository.active(profile.id()).orElseThrow();
            assertThat(active.rollbackVersion()).isNull();
            // Complete new evidence rejects the current route even though the catalog itself remains valid.
            next.set(new SelectionReport(null, Map.of(newEndpoint.key(), "QUALITY_OR_LATENCY"), 1, 2, true, "NO_ELIGIBLE_MODEL"));
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.revoked("replacement")).isTrue();
        }
        Endpoint missingCapability = new Endpoint("new", "allowed", Set.of(), 2000, 100, newEndpoint.prices(), true);
        catalog.set(new CatalogSnapshot("catalog3", clock.instant(), clock.instant().plusSeconds(7200),
                List.of(missingCapability), "newhash2"));
        next.set(report(policy("unsupported", missingCapability, "0.1", clock.instant())));
        try (var coordinator = coordinator(canary("0.1", Duration.ZERO), next::get)) {
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.active(profile.id())).isEmpty();
        }
    }

    @Test void incompleteOverBudgetAndDisallowedProposalsNeverActivate() throws Exception {
        try (var coordinator = coordinator(canary("0", Duration.ZERO), next::get)) {
            var proposal = next.get().proposedPolicy();
            next.set(new SelectionReport(proposal, Map.of(), 1, 2, false, "BUDGET_EXHAUSTED"));
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            next.set(report(policy("overbudget", newEndpoint, "1.1", clock.instant())));
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            Endpoint expensive = endpoint("expensive", "0.02");
            catalog.set(new CatalogSnapshot("catalog2", clock.instant(), clock.instant().plusSeconds(7200), List.of(expensive), "hash2"));
            next.set(report(policy("underestimated", expensive, "0.1", clock.instant())));
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.active(profile.id())).isEmpty();
        }
    }

    @Test void shadowReportsQualityRejectionWithoutMutatingActiveOrRevocationState() throws Exception {
        activate(policy("old1", oldEndpoint, "0.5", clock.instant()));
        next.set(new SelectionReport(null, Map.of(oldEndpoint.key(), "QUALITY_OR_LATENCY"), 1, 2, true, "NO_ELIGIBLE_MODEL"));
        try (var coordinator = coordinator(Settings.defaults(), next::get)) {
            assertThat(coordinator.runOnce().activeEligible()).isFalse();
            assertThat(repository.active(profile.id()).orElseThrow().version()).isEqualTo("old1");
            assertThat(repository.revoked("old1")).isFalse();
        }
    }

    private SelectionRefreshCoordinator coordinator(Settings settings, Supplier<SelectionReport> evaluate) {
        return new SelectionRefreshCoordinator(settings, repository, () -> profile, catalog::get, evaluate, clock, outcomes::add);
    }
    private SelectionRefreshCoordinator coordinator(Settings settings, Supplier<SelectionReport> evaluate,
            Supplier<ScheduledExecutorService> workers) {
        return new SelectionRefreshCoordinator(settings, repository, () -> profile, catalog::get, evaluate, clock, outcomes::add, workers);
    }
    private void activate(SelectionPolicy policy) throws Exception {
        repository.saveProposal(policy);
        repository.activate(policy.version(), p -> PolicyEligibility.valid(p, profile, catalog.get(), clock.instant()));
    }
    private SelectionPolicy policy(String version, Endpoint endpoint, String cost, Instant effective) {
        return new SelectionPolicy(version, profile.id(), profile.version(), catalog.get().version(),
                List.of(new QualifiedCandidate(endpoint, d(cost), d("0.95"), 100, d("0.95"), "benchmark1")),
                effective, effective.plusSeconds(3600), null);
    }
    private static SelectionReport report(SelectionPolicy policy) { return new SelectionReport(policy, Map.of(), 1, 2, true, "QUALIFIED"); }
    private static Settings canary(String benefit, Duration delay) { return new Settings(false, Mode.CANARY, Duration.ofSeconds(30), d(benefit), delay); }
    private static Endpoint endpoint(String id, String promptRate) {
        return new Endpoint(id, "allowed", Set.of("response_format"), 2000, 100,
                new PriceRates(d(promptRate), d("0.001"), d("0"), null, null), true);
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static final class MutableClock extends Clock {
        private Instant value = Instant.parse("2026-09-19T12:00:00Z");
        void advance(Duration duration) { value = value.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }
}
