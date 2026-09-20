package com.opencrm.ai.selection;

import static com.opencrm.ai.selection.SelectionTypes.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, replayable ranking. No network calls and no policy activation. */
public final class SelectionEngine {
    public SelectionReport select(TaskProfile profile, CatalogSnapshot catalog, List<EvaluationEvidence> evidence,
            Instant now, String policyVersion, String rollbackVersion, boolean complete) {
        Map<String, String> rejected = new LinkedHashMap<>();
        Map<String, EvaluationEvidence> byEndpoint = new HashMap<>();
        for (var item : evidence) {
            if (byEndpoint.putIfAbsent(item.endpointKey(), item) != null)
                return new SelectionReport(null, Map.of(item.endpointKey(), "DUPLICATE_EVIDENCE"),
                        evidence.size(), catalog.endpoints().size(), false, "AMBIGUOUS_EVIDENCE");
        }
        if (!complete) return new SelectionReport(null, Map.of(), evidence.size(), catalog.endpoints().size(),
                false, "EVALUATION_INCOMPLETE");
        List<QualifiedCandidate> routes = new ArrayList<>();
        Instant expiresAt = earlier(now.plus(profile.policyTtl()), catalog.expiresAt());
        for (Endpoint endpoint : catalog.endpoints()) {
            String reason = rejection(endpoint, profile, catalog, byEndpoint.get(endpoint.key()), now);
            if (reason != null) { rejected.put(endpoint.key(), reason); continue; }
            EvaluationEvidence item = byEndpoint.get(endpoint.key());
            BigDecimal cost = item.totalCost().divide(BigDecimal.valueOf(item.successes()), 18, RoundingMode.CEILING);
            routes.add(new QualifiedCandidate(endpoint, cost, item.quality(), item.p95LatencyMillis(),
                    item.verdict().confidence(), item.benchmarkVersion()));
            expiresAt = earlier(expiresAt, item.evaluatedAt().plus(profile.evidenceTtl()));
        }
        routes.sort(Comparator.comparing(QualifiedCandidate::expectedCostPerSuccess)
                .thenComparing(QualifiedCandidate::quality, Comparator.reverseOrder())
                .thenComparingLong(QualifiedCandidate::p95LatencyMillis)
                .thenComparing(route -> route.endpoint().key()));
        SelectionPolicy policy = routes.isEmpty() ? null : new SelectionPolicy(policyVersion, profile.id(),
                profile.version(), catalog.version(), routes, now, expiresAt, rollbackVersion);
        return new SelectionReport(policy, rejected, evidence.size(), catalog.endpoints().size(), true,
                policy == null ? "NO_ELIGIBLE_MODEL" : "LOWEST_COST_IN_EVALUATED_SET");
    }

    public static boolean eligible(Endpoint endpoint, TaskProfile profile, CatalogSnapshot catalog, Instant now) {
        return !now.isBefore(catalog.fetchedAt()) && now.isBefore(catalog.expiresAt())
                && endpoint.available() && profile.allowedProviders().contains(endpoint.provider())
                && endpoint.supportedParameters().containsAll(profile.requiredParameters())
                && (long) endpoint.contextLength() >= (long) profile.maxInputTokens() + profile.maxOutputTokens()
                && endpoint.maxCompletionTokens() >= profile.maxOutputTokens()
                && validPrices(endpoint.prices())
                && catalog.endpoints().stream().filter(e -> e.key().equals(endpoint.key())).count() == 1
                && catalog.endpoints().contains(endpoint);
    }

    private String rejection(Endpoint endpoint, TaskProfile profile, CatalogSnapshot catalog,
            EvaluationEvidence evidence, Instant now) {
        if (!eligible(endpoint, profile, catalog, now)) return "CAPABILITY_PROVIDER_PRICE_OR_CATALOG";
        if (evidence == null) return "NOT_EVALUATED";
        if (!profile.id().equals(evidence.profileId()) || !profile.version().equals(evidence.profileVersion())
                || !catalog.version().equals(evidence.catalogVersion())) return "EVIDENCE_VERSION_MISMATCH";
        if (evidence.evaluatedAt() == null || evidence.evaluatedAt().isAfter(now)
                || !now.isBefore(evidence.evaluatedAt().plus(profile.evidenceTtl()))) return "STALE_EVIDENCE";
        if (!evidence.capabilityProbePassed() || evidence.attempts() < profile.minSamples()
                || evidence.successes() < profile.minSamples()) return "INSUFFICIENT_SUCCESSFUL_PROBES";
        if (evidence.quality().compareTo(profile.minQuality()) < 0
                || evidence.p95LatencyMillis() > profile.maxP95LatencyMillis()) return "QUALITY_OR_LATENCY";
        JevVerdict verdict = evidence.verdict();
        if (verdict == null || !verdict.suitable() || verdict.confidence().compareTo(profile.minConfidence()) < 0
                || verdict.probabilities().get("suitable").compareTo(profile.minConfidence()) < 0)
            return "JEV_ABSTAINED_OR_REJECTED";
        if (evidence.totalCost().divide(BigDecimal.valueOf(evidence.successes()), 18, RoundingMode.CEILING)
                .compareTo(profile.maxCostPerTask()) > 0) return "OVER_BUDGET";
        return null;
    }

    private static boolean validPrices(PriceRates rates) {
        return rates != null && nonnegativeRate(rates.prompt()) && nonnegativeRate(rates.completion())
                && nonnegativeRate(rates.request())
                && (rates.cacheRead() == null || nonnegativeRate(rates.cacheRead()))
                && (rates.cacheWrite() == null || nonnegativeRate(rates.cacheWrite()));
    }
    private static boolean nonnegativeRate(BigDecimal value) { return value != null && value.signum() >= 0; }
    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
