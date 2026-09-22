package com.opencrm.ai.selection.execution;

import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.execution.ExecutionTypes.*;
import com.opencrm.ai.selection.policy.FilePolicyRepository;
import com.opencrm.ai.selection.policy.PolicyEligibility;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A dispatch never trusts cached eligibility and never runs tool actions or retries a returned result. */
public final class PolicyExecutor {
    private final FilePolicyRepository policies;
    private final Supplier<TaskProfile> profiles;
    private final Supplier<CatalogSnapshot> catalogs;
    private final OpenRouterExecutionClient client;
    private final Clock clock;
    private final Consumer<Audit> audit;
    private final int maxAttempts;

    public PolicyExecutor(FilePolicyRepository policies, Supplier<TaskProfile> profiles,
            Supplier<CatalogSnapshot> catalogs, OpenRouterExecutionClient client, Clock clock,
            Consumer<Audit> audit, int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 5) throw new IllegalArgumentException("Attempts must be bounded at 1..5");
        this.policies = policies; this.profiles = profiles; this.catalogs = catalogs;
        this.client = client; this.clock = clock; this.audit = audit; this.maxAttempts = maxAttempts;
    }

    public Completion execute(Request request) {
        TaskProfile profile = profiles.get();
        if (client.inputAllowance(request) > profile.maxInputTokens()) throw new Unavailable("INPUT_LIMIT");
        if (request.responseFormat() == null && profile.requiredParameters().contains("response_format"))
            throw new Unavailable("REQUIRED_RESPONSE_FORMAT_MISSING");
        if (request.tools().isEmpty() && profile.requiredParameters().contains("tools"))
            throw new Unavailable("REQUIRED_TOOLS_MISSING");
        if (request.responseFormat() != null && !profile.requiredParameters().contains("response_format"))
            throw new Unavailable("UNQUALIFIED_RESPONSE_FORMAT");
        if (!request.tools().isEmpty() && (!profile.requiredParameters().contains("tools")
                || !profile.requiredParameters().contains("parallel_tool_calls"))) throw new Unavailable("UNQUALIFIED_TOOLS");
        SelectionPolicy policy = active(profile);
        BigDecimal committed = BigDecimal.ZERO;
        int attempt = 0;
        for (QualifiedCandidate candidate : policy.routes()) {
            if (attempt >= maxAttempts) break;
            // A refresh, revocation or profile edit while a previous attempt ran invalidates this dispatch.
            TaskProfile latest = profiles.get();
            SelectionPolicy current = active(latest);
            if (!latest.equals(profile) || !current.version().equals(policy.version())) throw new Unavailable("POLICY_CHANGED");
            BigDecimal reservation = maximumCost(candidate.endpoint(), profile);
            if (committed.add(reservation).compareTo(profile.maxCostPerTask()) > 0) continue;
            attempt++;
            long started = System.nanoTime();
            try {
                Completion result = client.complete(candidate.endpoint(), profile, request);
                long elapsed = elapsed(started);
                BigDecimal actual = result.usage().reportedCost();
                boolean breached = actual.compareTo(reservation) > 0
                        || committed.add(actual).compareTo(profile.maxCostPerTask()) > 0
                        || result.usage().inputTokens() > profile.maxInputTokens()
                        || result.usage().outputTokens() > profile.maxOutputTokens();
                if (breached) revokeAndRollback(policy, profile);
                audit.accept(new Audit(clock.instant(), profile.id(), policy.version(), candidate.endpoint().key(),
                        attempt, elapsed, reservation, actual, result.usage(), breached ? "BUDGET_VIOLATION" : "SUCCESS"));
                if (breached) {
                    throw new Unavailable("BUDGET_VIOLATION");
                }
                return result;
            } catch (TransportFailure failure) {
                // An uncertain failed call may still be charged. Never reclaim its full reservation.
                committed = committed.add(reservation);
                audit.accept(new Audit(clock.instant(), profile.id(), policy.version(), candidate.endpoint().key(),
                        attempt, elapsed(started), reservation, null, null, failure.getMessage()));
                if (!failure.retryable() || request.containsToolResults()) throw new Unavailable(failure.getMessage());
            }
        }
        throw new Unavailable(attempt == 0 ? "NO_BUDGET_COMPLIANT_ROUTE" : "ELIGIBLE_ROUTES_EXHAUSTED");
    }

    private SelectionPolicy active(TaskProfile profile) {
        try {
            var policy = policies.active(profile.id()).orElseThrow(() -> new Unavailable("NO_ACTIVE_POLICY"));
            if (!PolicyEligibility.valid(policy, profile, catalogs.get(), clock.instant())) throw new Unavailable("STALE_OR_INELIGIBLE_POLICY");
            return policy;
        } catch (IOException | IllegalArgumentException e) { throw new Unavailable("POLICY_STORAGE_UNAVAILABLE"); }
    }

    private void revokeAndRollback(SelectionPolicy policy, TaskProfile profile) {
        try {
            try { policies.rollback(profile.id(), p -> PolicyEligibility.valid(p, profile, catalogs.get(), clock.instant())); }
            catch (IllegalStateException ignored) { /* Revocation below still prevents reuse. */ }
            finally { policies.revoke(policy.version()); }
        } catch (IOException e) { throw new Unavailable("POLICY_REVOCATION_FAILED"); }
    }

    public static BigDecimal maximumCost(Endpoint endpoint, TaskProfile profile) {
        PriceRates rates = endpoint.prices();
        BigDecimal input = rates.prompt();
        if (rates.cacheRead() != null) input = input.max(rates.cacheRead());
        if (rates.cacheWrite() != null) input = input.max(rates.cacheWrite());
        return input.multiply(BigDecimal.valueOf(profile.maxInputTokens()))
                .add(rates.completion().multiply(BigDecimal.valueOf(profile.maxOutputTokens())))
                .add(rates.request());
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
