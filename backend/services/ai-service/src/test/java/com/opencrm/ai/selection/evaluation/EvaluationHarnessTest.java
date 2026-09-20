package com.opencrm.ai.selection.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class EvaluationHarnessTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = "typesafe/jev-1.13-20260917";
    private HttpServer server;
    private URI uri;
    private final AtomicInteger received = new AtomicInteger();
    private final List<String> bodies = new ArrayList<>();
    private java.util.function.Function<String, String> responder;
    private int status;

    @BeforeEach void setup() throws Exception {
        status = 200;
        responder = body -> response(body, "suitable", "0.95");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/decisions", exchange -> {
            received.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            bodies.add(body);
            byte[] result = responder.apply(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/decisions");
    }
    @AfterEach void cleanup() { server.stop(0); }

    private JevDecisionAdapter adapter(int retries) {
        return new JevDecisionAdapter(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                new JevDecisionAdapter.Config(uri, MODEL, "rubric-v1", Set.of("TypeSafe"),
                        Duration.ofSeconds(1), retries, new BigDecimal("0.1")), "test-only");
    }
    private JevDecisionAdapter.Request request() {
        return new JevDecisionAdapter.Request("candidate_0", "Correct constrained CPQ advice", Map.of("fixture_0", "synthetic evidence"));
    }
    private static String response(String body, String choice, String confidence) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("model", MODEL).put("provider", "TypeSafe");
            root.putObject("usage").put("cost", new BigDecimal("0.02"));
            ObjectNode answers = root.putObject("answers");
            JSON.readTree(body).path("questions").fieldNames().forEachRemaining(id -> {
                ObjectNode answer = answers.putObject(id);
                answer.put("type", "choice").put("choice", choice).put("confidence", new BigDecimal(confidence));
                answer.putObject("probabilities").put("suitable", new BigDecimal("0.95"))
                        .put("unsuitable", new BigDecimal("0.05"));
            });
            return JSON.writeValueAsString(root);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test void validatesTypedDecisionAndConstrainsProvider() throws Exception {
        EvaluationBudget budget = new EvaluationBudget(2, BigDecimal.ONE);
        var result = adapter(0).evaluate(request(), new BigDecimal("0.9"), budget, ignored -> {});
        assertThat(result.verdict().suitable()).isTrue();
        assertThat(result.verdict().evidenceRefs()).containsExactly("fixture_0");
        assertThat(budget.spent()).isEqualByComparingTo("0.02");
        var payload = JSON.readTree(bodies.getFirst());
        assertThat(payload.path("provider").path("allow_fallbacks").asBoolean()).isFalse();
        assertThat(payload.path("provider").path("only").get(0).asText()).isEqualTo("TypeSafe");
        assertThat(result.exchanges()).hasSize(1);
    }

    @Test void rejectsUnknownCandidateMissingEvidenceMalformedAndLowConfidence() {
        for (String kind : List.of("unknown", "missing", "distribution", "confidence", "wrongModel", "wrongProvider", "malformed", "contradiction")) {
            responder = body -> {
                try {
                    ObjectNode root = (ObjectNode) JSON.readTree(response(body, "suitable", "0.95"));
                    ObjectNode answers = (ObjectNode) root.path("answers");
                    switch (kind) {
                        case "unknown" -> answers.set("alien", answers.remove("candidate_0"));
                        case "missing" -> answers.remove("fixture_0");
                        case "distribution" -> ((ObjectNode) answers.path("candidate_0").path("probabilities")).put("suitable", 0.1);
                        case "confidence" -> ((ObjectNode) answers.path("candidate_0")).put("confidence", 0.2);
                        case "wrongModel" -> root.put("model", "untrusted/model");
                        case "wrongProvider" -> root.put("provider", "Other");
                        case "malformed" -> { return "{"; }
                        case "contradiction" -> ((ObjectNode) answers.path("candidate_0")).put("choice", "unsuitable");
                    }
                    return JSON.writeValueAsString(root);
                } catch (Exception e) { throw new IllegalStateException(e); }
            };
            assertThatThrownBy(() -> adapter(0).evaluate(request(), new BigDecimal("0.9"),
                    new EvaluationBudget(1, BigDecimal.ONE), ignored -> {}))
                    .as(kind).isInstanceOf(JevDecisionAdapter.InvalidDecision.class);
        }
    }

    @Test void retryStopsAtSharedCallCeilingAndRetainsFailedCostAndRawResponse() {
        status = 429;
        responder = body -> "{\"error\":\"rate limited\"}";
        EvaluationBudget budget = new EvaluationBudget(2, BigDecimal.ONE);
        List<JevDecisionAdapter.Exchange> trace = new ArrayList<>();
        assertThatThrownBy(() -> adapter(3).evaluate(request(), new BigDecimal("0.9"), budget, trace::add))
                .isInstanceOf(EvaluationBudget.Exhausted.class);
        assertThat(received.get()).isEqualTo(2);
        assertThat(trace).hasSize(2);
        assertThat(budget.spent()).isEqualByComparingTo("0.2");
    }

    @Test void refusesNetworkWhenSpendCannotCoverAttempt() {
        assertThatThrownBy(() -> adapter(0).evaluate(request(), BigDecimal.ONE,
                new EvaluationBudget(1, new BigDecimal("0.01")), ignored -> {}))
                .isInstanceOf(EvaluationBudget.Exhausted.class);
        assertThat(received.get()).isZero();
    }

    private TaskProfile profile() {
        return new TaskProfile("cpq-advice", "v1", Set.of("response_format"), 1000, 300,
                new BigDecimal("0.9"), new BigDecimal("0.9"), 5000, BigDecimal.ONE,
                Set.of("Provider"), 4, Duration.ofDays(1), Duration.ofHours(1));
    }
    private Endpoint candidate() {
        return new Endpoint("synthetic/model", "Provider", Set.of("response_format"), 4000, 1000,
                new PriceRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null), true);
    }
    private CatalogSnapshot catalog() {
        return new CatalogSnapshot("catalog-v1", Instant.now(), Instant.now().plusSeconds(3600), List.of(candidate()), "test-hash");
    }
    private EvaluationHarness.CandidateExecutor executor(boolean fails) {
        return new EvaluationHarness.CandidateExecutor() {
            public BigDecimal maximumCost(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) { return new BigDecimal("0.1"); }
            public EvaluationHarness.Generated generate(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) {
                return new EvaluationHarness.Generated(fails && f.heldOut() ? "{\"wrong\":true}" : f.expectedJson(), new BigDecimal("0.03"));
            }
        };
    }
    private EvaluationHarness.Report run(boolean fails, EvaluationBudget budget) {
        return new EvaluationHarness(Clock.systemUTC()).run(profile(), catalog(), List.of(candidate()),
                CpqEvaluationFixtures.structuredAdvice(), new EvaluationHarness.Settings("benchmark-v1", "Correct CPQ arithmetic", 2, 1),
                executor(fails), adapter(0), budget);
    }

    @Test void runsObjectiveFixturesIncludesEvaluatorOverheadAndReplays(@TempDir Path directory) throws Exception {
        var report = run(false, new EvaluationBudget(10, BigDecimal.ONE));
        assertThat(report.complete()).isTrue();
        assertThat(report.calls()).isEqualTo(5);
        assertThat(report.spend()).isEqualByComparingTo("0.14");
        var evidence = report.candidates().getFirst().evidence();
        assertThat(evidence.successes()).isEqualTo(4);
        assertThat(evidence.totalCost()).isEqualByComparingTo("0.14");
        assertThat(evidence.profileVersion()).isEqualTo("v1");
        assertThat(evidence.capabilityProbePassed()).isTrue();
        assertThat(report.candidates().getFirst().evaluatorExchanges()).hasSize(1);
        EvaluationReplayStore store = new EvaluationReplayStore();
        Path file = directory.resolve("report.json");
        store.save(file, report);
        assertThat(store.load(file)).isEqualTo(report);
        assertThatThrownBy(() -> store.save(file, report)).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
    }

    @Test void objectiveFailuresCannotBeOverriddenByConfidentEvaluator() {
        var evidence = run(true, new EvaluationBudget(10, BigDecimal.ONE)).candidates().getFirst().evidence();
        assertThat(evidence.quality()).isEqualByComparingTo("0.5");
        assertThat(evidence.successes()).isEqualTo(2);
        assertThat(evidence.capabilityProbePassed()).isFalse();
        assertThat(evidence.totalCost()).isEqualByComparingTo("0.14");
    }

    @Test void budgetExhaustionYieldsPartialEvidenceAndNoUnsupportedWinner() {
        var report = run(false, new EvaluationBudget(2, BigDecimal.ONE));
        assertThat(report.complete()).isFalse();
        assertThat(report.calls()).isEqualTo(2);
        assertThat(received.get()).isZero();
        assertThat(report.candidates().getFirst().reason()).isEqualTo("BUDGET_EXHAUSTED");
        assertThat(report.candidates().getFirst().evidence().verdict()).isNull();
    }

    @Test void overbillingStopsFutureCallsAndIsNeverDiscounted() {
        EvaluationBudget budget = new EvaluationBudget(2, BigDecimal.ONE);
        budget.reserve(new BigDecimal("0.1")).reconcile(new BigDecimal("0.5"));
        assertThat(budget.spent()).isEqualByComparingTo("0.5");
        assertThat(budget.breached()).isTrue();
        assertThatThrownBy(() -> budget.reserve(BigDecimal.ZERO)).isInstanceOf(EvaluationBudget.Exhausted.class);
    }


    @Test void failedGenerationRemainsBillableAndCountsAsObjectiveFailure() {
        EvaluationHarness.CandidateExecutor broken = new EvaluationHarness.CandidateExecutor() {
            public BigDecimal maximumCost(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) { return new BigDecimal("0.1"); }
            public EvaluationHarness.Generated generate(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) throws Exception {
                throw new java.io.IOException("synthetic timeout");
            }
        };
        var report = new EvaluationHarness(Clock.systemUTC()).run(profile(), catalog(), List.of(candidate()),
                CpqEvaluationFixtures.toolArguments(), new EvaluationHarness.Settings("tool-v1", "Exact safe tool proposal", 2, 1),
                broken, adapter(0), new EvaluationBudget(10, BigDecimal.ONE));
        var evidence = report.candidates().getFirst().evidence();
        assertThat(evidence.successes()).isZero();
        assertThat(evidence.totalCost()).isEqualByComparingTo("0.42");
        assertThat(report.candidates().getFirst().samples()).allMatch(s -> !s.passed() && s.cost().compareTo(new BigDecimal("0.1")) == 0);
    }

    @Test void timeoutIsBoundedAndRetainsConservativeCharge() {
        responder = body -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return response(body, "suitable", "0.95");
        };
        var client = new JevDecisionAdapter(HttpClient.newHttpClient(),
                new JevDecisionAdapter.Config(uri, MODEL, "rubric-v1", Set.of("TypeSafe"),
                        Duration.ofMillis(10), 0, new BigDecimal("0.1")), "test");
        EvaluationBudget budget = new EvaluationBudget(1, BigDecimal.ONE);
        List<JevDecisionAdapter.Exchange> trace = new ArrayList<>();
        assertThatThrownBy(() -> client.evaluate(request(), new BigDecimal("0.9"), budget, trace::add))
                .isInstanceOf(JevDecisionAdapter.InvalidDecision.class);
        assertThat(budget.spent()).isEqualByComparingTo("0.1");
        assertThat(trace).hasSize(1);
        assertThat(trace.getFirst().status()).isZero();
    }

    @Test void insufficientMinimumSamplesRejectsBeforeAnyCalls() {
        EvaluationBudget budget = new EvaluationBudget(10, BigDecimal.ONE);
        assertThatThrownBy(() -> new EvaluationHarness(Clock.systemUTC()).run(profile(), catalog(), List.of(candidate()),
                CpqEvaluationFixtures.toolArguments(), new EvaluationHarness.Settings("tool-v1", "Safe arguments", 1, 1),
                executor(false), adapter(0), budget)).isInstanceOf(IllegalArgumentException.class);
        assertThat(budget.calls()).isZero();
    }

    @Test void toolArgumentsRejectExtraFieldsAndWrongTypes() {
        EvaluationHarness.CandidateExecutor invalid = new EvaluationHarness.CandidateExecutor() {
            public BigDecimal maximumCost(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) { return new BigDecimal("0.1"); }
            public EvaluationHarness.Generated generate(Endpoint c, TaskProfile p, EvaluationHarness.Fixture f) {
                return new EvaluationHarness.Generated(
                        "{\"tool\":\"getQuote\",\"arguments\":{\"quoteId\":\"42\",\"delete\":true}}", BigDecimal.ZERO);
            }
        };
        var report = new EvaluationHarness(Clock.systemUTC()).run(profile(), catalog(), List.of(candidate()),
                CpqEvaluationFixtures.toolArguments(), new EvaluationHarness.Settings("tool-v1", "Safe arguments", 2, 1),
                invalid, adapter(0), new EvaluationBudget(10, BigDecimal.ONE));
        assertThat(report.candidates().getFirst().evidence().successes()).isZero();
        assertThat(report.candidates().getFirst().evidence().capabilityProbePassed()).isFalse();
    }


    @Test void exactBillingDecimalAndMissingProviderAreValidated() {
        responder = body -> response(body, "suitable", "0.95").replace("0.02", "0.01234567890123456789");
        EvaluationBudget budget = new EvaluationBudget(1, BigDecimal.ONE);
        adapter(0).evaluate(request(), new BigDecimal("0.9"), budget, ignored -> {});
        assertThat(budget.spent()).isEqualByComparingTo("0.01234567890123456789");
        responder = body -> response(body, "suitable", "0.95").replace(",\"provider\":\"TypeSafe\"", "");
        assertThatThrownBy(() -> adapter(0).evaluate(request(), new BigDecimal("0.9"),
                new EvaluationBudget(1, BigDecimal.ONE), ignored -> {}))
                .isInstanceOf(JevDecisionAdapter.InvalidDecision.class);
    }

    @Test void oversizedResponseIsBoundedAndChargedConservatively() {
        responder = body -> "x".repeat(2 * 1024 * 1024 + 1);
        EvaluationBudget budget = new EvaluationBudget(1, BigDecimal.ONE);
        assertThatThrownBy(() -> adapter(0).evaluate(request(), new BigDecimal("0.9"), budget, ignored -> {}))
                .isInstanceOf(JevDecisionAdapter.InvalidDecision.class);
        assertThat(budget.spent()).isEqualByComparingTo("0.1");
    }

    @Test void oversizedEvidenceAndUnsafeEvaluatorConfigFailBeforeNetwork() {
        assertThatThrownBy(() -> new JevDecisionAdapter.Request("candidate", "rubric", Map.of("f", "x".repeat(65537))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevDecisionAdapter.Config(uri.resolve("/decisions?key=secret"), MODEL,
                "rubric", Set.of("TypeSafe"), Duration.ofSeconds(1), 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevDecisionAdapter.Config(uri, MODEL, "rubric",
                Set.of("TypeSafe"), Duration.ofMinutes(3), 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(received.get()).isZero();
    }

    @Test void fixtureSuiteCoversStructuredAdviceAndSafeToolArguments() {
        assertThat(CpqEvaluationFixtures.toolArguments()).hasSize(2);
        assertThat(CpqEvaluationFixtures.toolArguments().getLast().heldOut()).isTrue();
        assertThat(CpqEvaluationFixtures.structuredAdvice()).allMatch(EvaluationHarness.Fixture::capabilityProbe);
    }
}
