package com.opencrm.ai.selection.refresh;

import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.execution.PolicyExecutor;
import com.opencrm.ai.selection.policy.FilePolicyRepository;
import com.opencrm.ai.selection.policy.PolicyEligibility;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Opt-in refresh orchestration. The supplied evaluator owns all network authorization and run budgets. */
public final class SelectionRefreshCoordinator implements AutoCloseable {
    public enum Mode { SHADOW, CANARY }

    public record Settings(boolean schedulingEnabled, Mode mode, Duration interval,
            BigDecimal minimumBenefitFraction, Duration minimumSwitchInterval) {
        public Settings {
            Objects.requireNonNull(mode); Objects.requireNonNull(interval); Objects.requireNonNull(minimumSwitchInterval);
            if (interval.isNegative() || interval.isZero() || interval.toMillis() < 1
                    || minimumSwitchInterval.isNegative() || minimumBenefitFraction == null
                    || minimumBenefitFraction.signum() < 0 || minimumBenefitFraction.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("Invalid refresh cadence or hysteresis");
        }
        /** Safe preview defaults, with neither a worker thread nor policy activation. */
        public static Settings defaults() {
            return new Settings(false, Mode.SHADOW, Duration.ofMinutes(15), BigDecimal.ZERO, Duration.ZERO);
        }
    }

    /** Deliberately excludes exception bodies, credentials and customer payloads. */
    public record Outcome(Instant checkedAt, String reason, String activeVersion, boolean activeEligible,
            SelectionPolicy proposal, SelectionReport selection, boolean usedCachedEvidence) { }

    /** Negative findings need version binding even when no candidate qualifies and the proposal is null. */
    public record RefreshEvaluation(String profileId, String profileVersion, String catalogVersion, SelectionReport report) {
        public RefreshEvaluation {
            if (profileId == null || profileId.isBlank() || profileVersion == null || profileVersion.isBlank()
                    || catalogVersion == null || catalogVersion.isBlank() || report == null)
                throw new IllegalArgumentException("Version-bound refresh evidence required");
        }
    }

    private record Context(TaskProfile profile, CatalogSnapshot catalog) { }

    private final Settings settings;
    private final FilePolicyRepository policies;
    private final Supplier<TaskProfile> profiles;
    private final Supplier<CatalogSnapshot> catalogs;
    private final Supplier<RefreshEvaluation> evaluate;
    private final Clock clock;
    private final Consumer<Outcome> reports;
    private final Supplier<ScheduledExecutorService> workers;
    private volatile ScheduledExecutorService worker;
    private Context lastContext;
    private volatile Outcome lastOutcome;
    private volatile boolean closed;

    public SelectionRefreshCoordinator(Settings settings, FilePolicyRepository policies,
            Supplier<TaskProfile> profiles, Supplier<CatalogSnapshot> catalogs,
            Supplier<RefreshEvaluation> evaluate, Clock clock, Consumer<Outcome> reports) {
        this(settings, policies, profiles, catalogs, evaluate, clock, reports,
                () -> Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "opencrm-selection-refresh");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    SelectionRefreshCoordinator(Settings settings, FilePolicyRepository policies,
            Supplier<TaskProfile> profiles, Supplier<CatalogSnapshot> catalogs,
            Supplier<RefreshEvaluation> evaluate, Clock clock, Consumer<Outcome> reports,
            Supplier<ScheduledExecutorService> workers) {
        this.settings = Objects.requireNonNull(settings); this.policies = Objects.requireNonNull(policies);
        this.profiles = Objects.requireNonNull(profiles); this.catalogs = Objects.requireNonNull(catalogs);
        this.evaluate = Objects.requireNonNull(evaluate); this.clock = Objects.requireNonNull(clock);
        this.reports = Objects.requireNonNull(reports); this.workers = Objects.requireNonNull(workers);
    }

    /** No implicit start in the constructor. Fixed delay prevents overlapping provider evaluations. */
    public synchronized boolean start() {
        if (closed) throw new IllegalStateException("Refresh coordinator is closed");
        if (!settings.schedulingEnabled() || worker != null) return false;
        worker = workers.get();
        worker.scheduleWithFixedDelay(this::runScheduled, 0, settings.interval().toMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    /** An immediate trigger for price/capability/profile/quality events, independent of scheduled cadence. */
    public synchronized Outcome runOnce() {
        if (closed) throw new IllegalStateException("Refresh coordinator is closed");
        Context context;
        try { context = readContext(); }
        catch (RuntimeException unavailable) { return failure(lastContext, "EVIDENCE_UNAVAILABLE", true); }
        String expectedCurrentVersion;
        try { expectedCurrentVersion = version(policies.active(context.profile().id()).orElse(null)); }
        catch (IOException unavailable) { return failure(context, "POLICY_STORAGE_UNAVAILABLE", false); }
        RefreshEvaluation evaluation;
        try { evaluation = Objects.requireNonNull(evaluate.get()); }
        catch (RuntimeException unavailable) {
            // A failed supplier may already have refreshed its catalog/profile. Never assert stale retention.
            try { return failure(readContext(), "EVALUATOR_UNAVAILABLE", false); }
            catch (RuntimeException missing) { return failure(lastContext, "EVALUATOR_UNAVAILABLE", true); }
        }
        // Evaluation may refresh catalog files or take long enough for evidence to expire.
        try { context = readContext(); }
        catch (RuntimeException unavailable) { return failure(lastContext, "EVIDENCE_UNAVAILABLE", true); }
        try {
            synchronized (policies) {
                // Repository operations are synchronized on this same instance: compare and update are atomic
                // for the documented single-writer integration, including administrative/canary actions.
                context = readContext();
                Instant now = clock.instant();
                SelectionPolicy current = policies.active(context.profile().id()).orElse(null);
                SelectionReport report = evaluation.report();
                if (!Objects.equals(expectedCurrentVersion, version(current))) {
                    return publish(new Outcome(now, "SUPERSEDED_REFRESH", version(current), valid(current, context, now), null, report, false));
                }
                if (!bound(evaluation, context) || (report.proposedPolicy() != null
                        && !bound(report.proposedPolicy(), context))) {
                    return failure(context, "STALE_EVALUATION", false);
                }
                boolean complete = report.complete() && report.evaluatedCandidates() > 0
                        && report.evaluatedCandidates() <= report.catalogCandidates();
                boolean currentValid = valid(current, context, now);
                if (complete && current != null && explicitlyRejected(current, report)) currentValid = false;
                if (!currentValid && current != null && settings.mode() == Mode.CANARY) policies.revoke(current.version());
                SelectionPolicy proposal = report.proposedPolicy();
                if (!complete || proposal == null || proposal.routes().size() > report.evaluatedCandidates()
                        || !valid(proposal, context, now) || policies.revoked(proposal.version())) {
                    return publish(new Outcome(now, currentValid ? "RETAINED_NO_ELIGIBLE_PROPOSAL" : "UNAVAILABLE_NO_ELIGIBLE_PROPOSAL",
                            version(current), currentValid, null, report, false));
                }
                // Link only a still-eligible predecessor. An invalid policy is never a rollback target.
                if (proposal.rollbackVersion() != null
                        && (!currentValid || (!current.version().equals(proposal.version())
                        && !current.version().equals(proposal.rollbackVersion())))) {
                    return publish(new Outcome(now, "CONFLICTING_ROLLBACK_VERSION", version(current), currentValid, null, report, false));
                }
                if (currentValid && !current.version().equals(proposal.version()) && proposal.rollbackVersion() == null) {
                    proposal = new SelectionPolicy(proposal.version(), proposal.profileId(), proposal.profileVersion(),
                            proposal.catalogVersion(), proposal.routes(), proposal.effectiveAt(), proposal.expiresAt(), current.version());
                }
                policies.saveProposal(proposal);
                if (settings.mode() == Mode.SHADOW) {
                    return publish(new Outcome(now, "SHADOW_PROPOSAL", version(current), currentValid, proposal, report, false));
                }
                if (currentValid) {
                    String reason = retainReason(current, proposal, now);
                    if (reason != null) return publish(new Outcome(now, reason, current.version(), true, proposal, report, false));
                }
                SelectionPolicy selected = proposal;
                String expectedBeforeActivation = version(policies.active(context.profile().id()).orElse(null));
                policies.activate(selected.version(), candidate -> {
                    if (closed) return false;
                    Context latest = readContext();
                    try {
                        if (!Objects.equals(expectedBeforeActivation, version(policies.active(latest.profile().id()).orElse(null)))) return false;
                    } catch (IOException unavailable) { return false; }
                    return bound(evaluation, latest) && valid(candidate, latest, clock.instant());
                });
                return publish(new Outcome(clock.instant(), "ACTIVATED", selected.version(), true, selected, report, false));
            }
        } catch (IOException | RuntimeException failure) {
            return failure(context, "POLICY_UPDATE_FAILED", false);
        }
    }

    public Optional<Outcome> lastOutcome() { return Optional.ofNullable(lastOutcome); }

    private static boolean bound(RefreshEvaluation evaluation, Context context) {
        return evaluation.profileId().equals(context.profile().id())
                && evaluation.profileVersion().equals(context.profile().version())
                && evaluation.catalogVersion().equals(context.catalog().version());
    }

    private static boolean bound(SelectionPolicy policy, Context context) {
        return policy.profileId().equals(context.profile().id()) && policy.profileVersion().equals(context.profile().version())
                && policy.catalogVersion().equals(context.catalog().version());
    }

    private String retainReason(SelectionPolicy current, SelectionPolicy proposal, Instant now) {
        if (current.version().equals(proposal.version())) return "CURRENT_VERSION_RETAINED";
        if (current.routes().getFirst().endpoint().key().equals(proposal.routes().getFirst().endpoint().key()))
            return "CURRENT_PRIMARY_RETAINED";
        if (now.isBefore(current.effectiveAt().plus(settings.minimumSwitchInterval()))) return "MINIMUM_SWITCH_INTERVAL";
        BigDecimal oldCost = current.routes().getFirst().expectedCostPerSuccess();
        BigDecimal newCost = proposal.routes().getFirst().expectedCostPerSuccess();
        BigDecimal benefit = oldCost.subtract(newCost);
        if (benefit.signum() <= 0 || benefit.compareTo(oldCost.multiply(settings.minimumBenefitFraction())) < 0)
            return "MINIMUM_BENEFIT_NOT_MET";
        return null;
    }

    private Context readContext() {
        Context context = new Context(Objects.requireNonNull(profiles.get()), Objects.requireNonNull(catalogs.get()));
        lastContext = context;
        return context;
    }

    private boolean valid(SelectionPolicy policy, Context context, Instant now) {
        if (policy == null || context == null || !PolicyEligibility.valid(policy, context.profile(), context.catalog(), now)) return false;
        return policy.routes().stream().allMatch(route -> PolicyExecutor.maximumCost(route.endpoint(), context.profile())
                .compareTo(context.profile().maxCostPerTask()) <= 0);
    }

    private static boolean explicitlyRejected(SelectionPolicy current, SelectionReport report) {
        return current.routes().stream().anyMatch(route -> {
            String reason = report.rejected().get(route.endpoint().key());
            return reason != null && !reason.equals("NOT_EVALUATED");
        });
    }

    private Outcome failure(Context context, String reason, boolean cached) {
        Instant now = clock.instant();
        SelectionPolicy current = null;
        boolean eligible = false;
        try {
            if (context != null) {
                synchronized (policies) {
                    current = policies.active(context.profile().id()).orElse(null);
                    eligible = valid(current, context, now);
                    if (!eligible && current != null && settings.mode() == Mode.CANARY) policies.revoke(current.version());
                }
            }
        } catch (IOException | RuntimeException ignored) { /* Fail closed; never activate in an error path. */ }
        return publish(new Outcome(now, (eligible ? "RETAINED_" : "UNAVAILABLE_") + reason,
                version(current), eligible, null, null, cached));
    }

    private Outcome publish(Outcome outcome) {
        lastOutcome = outcome;
        try { reports.accept(outcome); }
        catch (RuntimeException ignored) { /* A failing observer must not kill future safety checks. */ }
        return outcome;
    }

    private static String version(SelectionPolicy policy) { return policy == null ? null : policy.version(); }

    private void runScheduled() {
        try { runOnce(); }
        catch (RuntimeException ignored) { /* Closing or a callback failure must not terminate the process. */ }
    }

    @Override public void close() {
        closed = true;
        ScheduledExecutorService activeWorker = worker;
        if (activeWorker != null) activeWorker.shutdownNow();
        // Cover a concurrent start that passed its closed check before close was called.
        synchronized (this) {
            if (worker != null && worker != activeWorker) worker.shutdownNow();
        }
    }
}
