package com.opencrm.ai.selection.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/** Synthetic/offline-capable benchmark. The executor generates JSON; it must never execute proposed tools. */
public final class EvaluationHarness {
    public record Fixture(String id, String prompt, String expectedJson, boolean heldOut, boolean capabilityProbe) {
        public Fixture {
            if (id == null || !id.matches("[A-Za-z0-9_-]+") || prompt == null || prompt.isBlank()
                    || expectedJson == null || expectedJson.isBlank()) throw new IllegalArgumentException("Invalid fixture");
        }
    }
    public record Settings(String benchmarkVersion, String rubric, int repetitions, int maximumCandidates) {
        public Settings {
            if (benchmarkVersion == null || benchmarkVersion.isBlank() || rubric == null || rubric.isBlank()
                    || repetitions < 1 || maximumCandidates < 1) throw new IllegalArgumentException("Explicit evaluation settings required");
        }
    }
    public record Generated(String rawResponse, BigDecimal actualCost, Usage usage, String model, String provider) {
        /** Offline fixture compatibility; absent usage is unknown, not zero. */
        public Generated(String rawResponse, BigDecimal actualCost) {
            this(rawResponse, actualCost, null, null, null);
        }
    }
    public interface CandidateExecutor {
        /** Guaranteed upper bound for one bounded call, including any overhead. Unknown prices must reject. */
        BigDecimal maximumCost(Endpoint candidate, TaskProfile profile, Fixture fixture);
        /** Exactly one bounded provider call; no hidden retries or tool actions. Failures remain charged at maximum. */
        Generated generate(Endpoint candidate, TaskProfile profile, Fixture fixture) throws Exception;
    }
    public record Sample(String candidateId, String fixtureId, int repetition, String rawResponse,
                         boolean passed, boolean heldOut, long latencyMillis, BigDecimal cost, String failure,
                         Usage usage, String model, String provider) { }
    public record CandidateResult(String candidateId, String endpointKey, EvaluationEvidence evidence,
                                  List<Sample> samples, List<JevDecisionAdapter.Exchange> evaluatorExchanges,
                                  String reason, BigDecimal confidenceSquaredError) { }
    public record Report(String benchmarkVersion, String profileId, String profileVersion, String catalogVersion,
                         int catalogCandidates, int coveredCandidates, int calls, BigDecimal spend,
                         boolean complete, List<CandidateResult> candidates, List<Fixture> fixtures,
                         TaskProfile profile, CatalogSnapshot catalog, JevDecisionAdapter.Config evaluatorConfig) { }

    private final ObjectMapper json = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Clock clock;
    public EvaluationHarness(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public Report run(TaskProfile profile, CatalogSnapshot catalog, List<Endpoint> candidates,
                      List<Fixture> fixtures, Settings settings, CandidateExecutor executor,
                      JevDecisionAdapter evaluator, EvaluationBudget budget) {
        if (fixtures.isEmpty() || fixtures.stream().noneMatch(Fixture::heldOut)
                || fixtures.stream().noneMatch(f -> !f.heldOut()) || fixtures.stream().noneMatch(Fixture::capabilityProbe)
                || fixtures.stream().map(Fixture::id).distinct().count() != fixtures.size()
                || (long) fixtures.size() * settings.repetitions() < profile.minSamples()
                || candidates.stream().map(Endpoint::key).distinct().count() != candidates.size()
                || candidates.stream().anyMatch(c -> !catalog.endpoints().contains(c)))
            throw new IllegalArgumentException("Insufficient, duplicated, or unbound benchmark coverage");
        for (Fixture fixture : fixtures) parse(fixture.expectedJson());
        List<CandidateResult> results = new ArrayList<>();
        boolean complete = candidates.size() <= settings.maximumCandidates();
        int limit = Math.min(candidates.size(), settings.maximumCandidates());
        for (int index = 0; index < limit; index++) {
            Endpoint endpoint = candidates.get(index);
            String candidateId = "candidate_" + index;
            BigDecimal before = budget.spent();
            List<Sample> samples = new ArrayList<>();
            List<JevDecisionAdapter.Exchange> exchanges = new ArrayList<>();
            String reason = "COMPLETE";
            JevVerdict verdict = null;
            try {
                for (Fixture fixture : fixtures) {
                    for (int repetition = 0; repetition < settings.repetitions(); repetition++) {
                        BigDecimal reserved = executor.maximumCost(endpoint, profile, fixture);
                        EvaluationBudget.Reservation reservation = budget.reserve(reserved);
                        BigDecimal sampleBefore = budget.spent();
                        long started = System.nanoTime();
                        String raw = "", failure = "";
                        Usage usage = null;
                        String model = null, provider = null;
                        boolean passed = false;
                        try {
                            Generated generated = executor.generate(endpoint, profile, fixture);
                            raw = generated.rawResponse();
                            usage = generated.usage();
                            model = generated.model();
                            provider = generated.provider();
                            reservation.reconcile(generated.actualCost());
                            passed = parse(fixture.expectedJson()).equals(parse(raw));
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new EvaluationBudget.Exhausted();
                        } catch (Exception ex) {
                            failure = "GENERATION_OR_SCHEMA_FAILURE";
                        }
                        samples.add(new Sample(candidateId, fixture.id(), repetition, raw, passed, fixture.heldOut(),
                                Duration.ofNanos(System.nanoTime() - started).toMillis(),
                                budget.spent().subtract(sampleBefore).add(reserved), failure, usage, model, provider));
                        if (budget.breached()) throw new EvaluationBudget.Exhausted();
                    }
                }
                Map<String, String> evidence = new TreeMap<>();
                for (Sample sample : samples) {
                    String id = sample.fixtureId() + "_" + sample.repetition();
                    Fixture fixture = fixtures.stream().filter(f -> f.id().equals(sample.fixtureId())).findFirst().orElseThrow();
                    // Blind endpoint identity. Objective facts cannot be overridden by the evaluator.
                    evidence.put(id, json.writeValueAsString(Map.of("prompt", fixture.prompt(),
                            "output", sample.rawResponse(), "objectivePassed", sample.passed(), "heldOut", sample.heldOut())));
                }
                verdict = evaluator.evaluate(new JevDecisionAdapter.Request(candidateId, settings.rubric(), evidence),
                        profile.minConfidence(), budget, exchanges::add).verdict();
            } catch (EvaluationBudget.Exhausted ex) {
                reason = "BUDGET_EXHAUSTED"; complete = false;
            } catch (Exception ex) {
                reason = "EVALUATOR_UNAVAILABLE_OR_INVALID"; complete = false;
            }
            int successes = (int) samples.stream().filter(Sample::passed).count();
            BigDecimal quality = samples.isEmpty() ? BigDecimal.ZERO :
                    BigDecimal.valueOf(successes).divide(BigDecimal.valueOf(samples.size()), 10, RoundingMode.HALF_UP);
            long[] latencies = samples.stream().mapToLong(Sample::latencyMillis).sorted().toArray();
            long p95 = latencies.length == 0 ? 0 : latencies[(int) Math.ceil(latencies.length * 0.95) - 1];
            boolean probes = fixtures.stream().filter(Fixture::capabilityProbe).allMatch(f ->
                    samples.stream().filter(s -> s.fixtureId().equals(f.id()) && s.passed()).count() == settings.repetitions());
            EvaluationEvidence result = new EvaluationEvidence(endpoint.key(), profile.id(), profile.version(), catalog.version(),
                    settings.benchmarkVersion(), samples.size(), successes, budget.spent().subtract(before), quality, p95,
                    probes, clock.instant(), verdict);
            BigDecimal calibration = verdict == null ? null :
                    verdict.probabilities().get("suitable").subtract(quality).pow(2);
            results.add(new CandidateResult(candidateId, endpoint.key(), result, List.copyOf(samples),
                    List.copyOf(exchanges), reason, calibration));
            if ("BUDGET_EXHAUSTED".equals(reason)) break;
        }
        return new Report(settings.benchmarkVersion(), profile.id(), profile.version(), catalog.version(),
                candidates.size(), results.size(), budget.calls(), budget.spent(),
                complete && results.size() == candidates.size() && !candidates.isEmpty(), List.copyOf(results), List.copyOf(fixtures), profile, catalog, evaluator.config());
    }
    private JsonNode parse(String raw) {
        try {
            JsonNode value = json.readTree(raw);
            if (value == null || !value.isObject()) throw new IllegalArgumentException("Expected a JSON object");
            return value;
        } catch (Exception ex) { throw new IllegalArgumentException("Malformed structured output"); }
    }
}
