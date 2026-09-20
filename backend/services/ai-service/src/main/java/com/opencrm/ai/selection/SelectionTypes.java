package com.opencrm.ai.selection;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned evidence contracts for ADR-014. Prices are USD per token/request, never per million. */
public final class SelectionTypes {
    private SelectionTypes() { }

    public record TaskProfile(String id, String version, Set<String> requiredParameters,
            int maxInputTokens, int maxOutputTokens, BigDecimal minQuality, BigDecimal minConfidence,
            long maxP95LatencyMillis, BigDecimal maxCostPerTask, Set<String> allowedProviders,
            int minSamples, Duration evidenceTtl, Duration policyTtl) {
        public TaskProfile {
            requireText(id); requireText(version);
            requiredParameters = Set.copyOf(requiredParameters);
            allowedProviders = Set.copyOf(allowedProviders);
            unit(minQuality); unit(minConfidence); nonnegative(maxCostPerTask);
            if (maxInputTokens < 1 || maxOutputTokens < 1 || maxP95LatencyMillis < 1 || minSamples < 1
                    || allowedProviders.isEmpty() || evidenceTtl.isNegative() || evidenceTtl.isZero()
                    || policyTtl.isNegative() || policyTtl.isZero()) throw new IllegalArgumentException("Invalid task profile");
        }
    }

    /** Null rates mean unknown; optional cache rates must be present when cache usage is charged. */
    public record PriceRates(BigDecimal prompt, BigDecimal completion, BigDecimal request,
            BigDecimal cacheRead, BigDecimal cacheWrite) { }

    public record Endpoint(String modelId, String provider, Set<String> supportedParameters,
            int contextLength, int maxCompletionTokens, PriceRates prices, boolean available) {
        public Endpoint {
            requireText(modelId); requireText(provider);
            supportedParameters = Set.copyOf(supportedParameters);
        }
        public String key() { return modelId + "@" + provider; }
    }

    public record CatalogSnapshot(String version, Instant fetchedAt, Instant expiresAt,
            List<Endpoint> endpoints, String rawHash) {
        public CatalogSnapshot {
            requireText(version); requireText(rawHash);
            endpoints = List.copyOf(endpoints);
            if (!expiresAt.isAfter(fetchedAt)) throw new IllegalArgumentException("Invalid catalog lifetime");
        }
    }

    /** Completion tokens include reasoning tokens; never charge reasoning a second time. */
    public record Usage(long inputTokens, long outputTokens, long cacheReadTokens,
            long cacheWriteTokens, long requests, BigDecimal reportedCost) {
        public Usage {
            if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0
                    || requests < 0 || cacheReadTokens > inputTokens || cacheWriteTokens > inputTokens - cacheReadTokens)
                throw new IllegalArgumentException("Invalid usage");
            if (reportedCost != null) nonnegative(reportedCost);
        }
    }

    public record JevVerdict(boolean suitable, BigDecimal confidence, Map<String, BigDecimal> probabilities,
            String evaluatorVersion, String rubricVersion, List<String> evidenceRefs) {
        public JevVerdict {
            unit(confidence); requireText(evaluatorVersion); requireText(rubricVersion);
            probabilities = Map.copyOf(probabilities); evidenceRefs = List.copyOf(evidenceRefs);
            if (!probabilities.keySet().equals(Set.of("suitable", "unsuitable")) || evidenceRefs.isEmpty())
                throw new IllegalArgumentException("Incomplete Jev evidence");
            probabilities.values().forEach(SelectionTypes::unit);
            BigDecimal sum = probabilities.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.02")) > 0)
                throw new IllegalArgumentException("Invalid Jev distribution");
            String selected = suitable ? "suitable" : "unsuitable";
            String other = suitable ? "unsuitable" : "suitable";
            if (probabilities.get(selected).compareTo(probabilities.get(other)) < 0)
                throw new IllegalArgumentException("Jev verdict contradicts distribution");
        }
    }

    public record EvaluationEvidence(String endpointKey, String benchmarkVersion, int attempts, int successes,
            BigDecimal totalCost, BigDecimal quality, long p95LatencyMillis, boolean capabilityProbePassed,
            Instant evaluatedAt, JevVerdict verdict) {
        public EvaluationEvidence {
            requireText(endpointKey); requireText(benchmarkVersion); nonnegative(totalCost); unit(quality);
            if (attempts < 0 || successes < 0 || successes > attempts || p95LatencyMillis < 0)
                throw new IllegalArgumentException("Invalid benchmark evidence");
        }
    }

    public record QualifiedCandidate(Endpoint endpoint, BigDecimal expectedCostPerSuccess,
            BigDecimal quality, long p95LatencyMillis, BigDecimal confidence, String benchmarkVersion) { }

    public record SelectionPolicy(String version, String profileId, String profileVersion, String catalogVersion,
            List<QualifiedCandidate> routes, Instant effectiveAt, Instant expiresAt, String rollbackVersion) {
        public SelectionPolicy {
            requireText(version); requireText(profileId); requireText(profileVersion); requireText(catalogVersion);
            routes = List.copyOf(routes);
            if (routes.isEmpty() || !expiresAt.isAfter(effectiveAt)) throw new IllegalArgumentException("Invalid policy");
        }
    }

    public record SelectionReport(SelectionPolicy proposedPolicy, Map<String, String> rejected,
            int evaluatedCandidates, int catalogCandidates, boolean complete, String reason) {
        public SelectionReport { rejected = Map.copyOf(rejected); }
    }

    static void requireText(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing identifier");
    }
    static void nonnegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Missing or negative amount");
    }
    static void unit(BigDecimal value) {
        nonnegative(value);
        if (value.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("Expected probability in [0,1]");
    }
}
