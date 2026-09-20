package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.execution.ExecutionTypes.*;
import com.opencrm.ai.selection.policy.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PolicyExecutionTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Instant now = Instant.parse("2026-09-19T12:00:00Z");
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<Audit> audits = new ArrayList<>();
    private final Queue<Reply> replies = new ArrayDeque<>();
    private HttpServer server;
    private FilePolicyRepository policies;
    private TaskProfile profile;
    private CatalogSnapshot catalog;
    private OpenRouterExecutionClient client;
    private final Endpoint first = endpoint("test/primary", "provider-a");
    private final Endpoint fallback = endpoint("test/fallback", "provider-b");

    @BeforeEach void setup() throws Exception {
        policies = new FilePolicyRepository(directory, mapper);
        profile = profile(new BigDecimal("0.01"));
        catalog = new CatalogSnapshot("catalog-1", now.minusSeconds(5), now.plusSeconds(3600), List.of(first, fallback), "hash");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            Reply reply = replies.remove();
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new OpenRouterExecutionClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"),
                "synthetic-test-key", Duration.ofSeconds(2), mapper);
    }

    @AfterEach void cleanup() { server.stop(0); }

    @Test void successfulRequestPinsProviderAndReconcilesCost() throws Exception {
        activate(policy("v1", List.of(first), null));
        replies.add(success(first, "0.00004"));
        Completion result = executor(2).execute(request());
        assertEquals("safe answer", result.content());
        JsonNode sent = requests.getFirst();
        assertEquals("provider-a", sent.path("provider").path("only").get(0).asText());
        assertFalse(sent.path("provider").path("allow_fallbacks").asBoolean(true));
        assertTrue(sent.path("provider").path("require_parameters").asBoolean());
        assertEquals("deny", sent.path("provider").path("data_collection").asText());
        assertEquals(100, sent.path("max_tokens").asInt());
        assertEquals(new BigDecimal("1.000000"), sent.path("provider").path("max_price").path("prompt").decimalValue().setScale(6));
        assertFalse(sent.path("stream").asBoolean(true));
        assertEquals(0, new BigDecimal("0.00004").compareTo(audits.getFirst().actualCost()));
        assertFalse(mapper.writeValueAsString(audits).contains("sensitive synthetic payload"));
    }

    @Test void uncertainFailuresReserveBudgetBeforeEligibleFallback() throws Exception {
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(new Reply(503, "{\"error\":\"unavailable\"}"));
        replies.add(success(fallback, "0.00004"));
        assertEquals(fallback.modelId(), executor(2).execute(request()).model());
        assertEquals(2, requests.size());
        assertEquals("HTTP_503", audits.getFirst().outcome());
        assertNull(audits.getFirst().actualCost());
        assertEquals(new BigDecimal("0.001200"), audits.getFirst().reservedCost().setScale(6));
    }

    @Test void failedCallCannotSpendSameBudgetTwice() throws Exception {
        profile = profile(new BigDecimal("0.0015"));
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(new Reply(503, "{}"));
        assertEquals("ELIGIBLE_ROUTES_EXHAUSTED", assertThrows(Unavailable.class, () -> executor(2).execute(request())).reason());
        assertEquals(1, requests.size());
    }

    @Test void toolResultRequestsAreNeverReplayedOnFailure() throws Exception {
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(new Reply(503, "{}"));
        assertThrows(Unavailable.class, () -> executor(2).execute(new Request("", "tool result", null, List.of(), true)));
        assertEquals(1, requests.size());
    }

    @Test void providerMismatchAndMalformedSuccessNeverTriggerFallback() throws Exception {
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(success(endpoint(first.modelId(), "unapproved-provider"), "0.00004"));
        assertEquals("PROVIDER_MISMATCH", assertThrows(Unavailable.class, () -> executor(2).execute(request())).reason());
        assertEquals(1, requests.size());
    }

    @Test void expiredPolicyAndRefreshedCatalogFailBeforeNetwork() throws Exception {
        activate(policy("v1", List.of(first), null));
        catalog = new CatalogSnapshot("catalog-2", now, now.plusSeconds(3600), List.of(first), "new-hash");
        assertEquals("STALE_OR_INELIGIBLE_POLICY", assertThrows(Unavailable.class, () -> executor(1).execute(request())).reason());
        assertEquals(0, requests.size());
    }

    @Test void policyAndCatalogExpiryAndRestrictionsAreRechecked() throws Exception {
        SelectionPolicy expired = new SelectionPolicy("old", profile.id(), profile.version(), catalog.version(),
                List.of(qualified(first)), now.minusSeconds(100), now.minusSeconds(1), null);
        policies.saveProposal(expired);
        assertThrows(IllegalStateException.class, () -> policies.activate("old", this::valid));
        var restricted = endpoint("test/restricted", "forbidden");
        policies.saveProposal(policy("restricted", List.of(restricted), null));
        assertThrows(IllegalStateException.class, () -> policies.activate("restricted", this::valid));
        var poor = new QualifiedCandidate(first, new BigDecimal("0.0001"), new BigDecimal("0.2"), 1,
                new BigDecimal("0.99"), "bench-1");
        var policy = new SelectionPolicy("poor", profile.id(), profile.version(), catalog.version(), List.of(poor),
                now, now.plusSeconds(60), null);
        assertFalse(valid(policy));
        assertEquals(0, requests.size());
    }

    @Test void revocationsAndActivePointersSurviveRestartAndVersionsAreImmutable() throws Exception {
        SelectionPolicy initial = policy("v1", List.of(first), null);
        activate(initial);
        FilePolicyRepository restarted = new FilePolicyRepository(directory, mapper);
        assertEquals(initial, restarted.active(profile.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> restarted.saveProposal(policy("v1", List.of(fallback), null)));
        restarted.revoke("v1");
        assertTrue(policies.active(profile.id()).isEmpty());
        assertThrows(IllegalStateException.class, () -> policies.activate("v1", this::valid));
    }

    @Test void dryRunSaveNeverActivatesAndRollbackRejectsRevokedVersion() throws Exception {
        policies.saveProposal(policy("v1", List.of(first), null));
        assertTrue(policies.active(profile.id()).isEmpty());
        activate(policy("v2", List.of(fallback), "v1"));
        policies.rollback(profile.id(), this::valid);
        assertEquals("v1", policies.active(profile.id()).orElseThrow().version());
        policies.activate("v2", this::valid);
        policies.revoke("v1");
        assertThrows(IllegalStateException.class, () -> policies.rollback(profile.id(), this::valid));
        assertEquals("v2", policies.active(profile.id()).orElseThrow().version());
    }

    @Test void costViolationRevokesCurrentPolicyAndRestoresEligiblePredecessor() throws Exception {
        policies.saveProposal(policy("v1", List.of(fallback), null));
        activate(policy("v2", List.of(first), "v1"));
        replies.add(success(first, "0.02"));
        assertEquals("BUDGET_VIOLATION", assertThrows(Unavailable.class, () -> executor(2).execute(request())).reason());
        assertTrue(policies.revoked("v2"));
        assertEquals("v1", policies.active(profile.id()).orElseThrow().version());
        assertEquals(1, requests.size());
    }

    @Test void oversizedInputAndMissingBudgetMakeNoPaidCall() throws Exception {
        activate(policy("v1", List.of(first), null));
        assertEquals("INPUT_LIMIT", assertThrows(Unavailable.class,
                () -> executor(1).execute(new Request("", "x".repeat(2000), null, List.of(), false))).reason());
        profile = profile(new BigDecimal("0.0002"));
        assertEquals("NO_BUDGET_COMPLIANT_ROUTE", assertThrows(Unavailable.class, () -> executor(1).execute(request())).reason());
        assertEquals(0, requests.size());
    }

    @Test void boundedAttemptsDoNotCycleThroughRoutes() throws Exception {
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(new Reply(429, "{}"));
        assertThrows(Unavailable.class, () -> executor(1).execute(request()));
        assertEquals(1, requests.size());
    }

    @Test void canaryQualityRegressionRollsBackWithoutReplayingAnyRequest() throws Exception {
        policies.saveProposal(policy("v1", List.of(first), null));
        activate(policy("v2", List.of(fallback), "v1"));
        var monitor = new CanaryMonitor(policies);
        assertEquals("ROLLED_BACK", monitor.observe(profile, catalog, now,
                new CanaryMonitor.Observation("v2", new BigDecimal("0.4"), new BigDecimal("0.0001"), 1, 20)));
        assertTrue(policies.revoked("v2"));
        assertEquals("v1", policies.active(profile.id()).orElseThrow().version());
        assertEquals("SUPERSEDED_OBSERVATION", monitor.observe(profile, catalog, now,
                new CanaryMonitor.Observation("v2", BigDecimal.ZERO, BigDecimal.ZERO, 0, 20)));
        assertEquals(0, requests.size());
    }

    @Test void distinctRoutingTagAndResponseDisplayNameAreSupported() throws Exception {
        var tagged = new Endpoint(first.modelId(), "provider-a/region", first.supportedParameters(), first.contextLength(),
                first.maxCompletionTokens(), first.prices(), true, "Provider A");
        profile = new TaskProfile(profile.id(), profile.version(), profile.requiredParameters(), profile.maxInputTokens(),
                profile.maxOutputTokens(), profile.minQuality(), profile.minConfidence(), profile.maxP95LatencyMillis(),
                profile.maxCostPerTask(), Set.of(tagged.provider()), profile.minSamples(), profile.evidenceTtl(), profile.policyTtl());
        catalog = new CatalogSnapshot(catalog.version(), catalog.fetchedAt(), catalog.expiresAt(), List.of(tagged), catalog.rawHash());
        activate(policy("v1", List.of(tagged), null));
        replies.add(success(endpoint(tagged.modelId(), "Provider A"), "0.00004"));
        assertEquals("Provider A", executor(1).execute(request()).provider());
        assertEquals("provider-a/region", requests.getFirst().path("provider").path("only").get(0).asText());
    }

    @Test void malformedStructuredContentNeverRetriesOrReturnsUnvalidatedData() throws Exception {
        profile = new TaskProfile(profile.id(), profile.version(), Set.of("response_format"), profile.maxInputTokens(),
                profile.maxOutputTokens(), profile.minQuality(), profile.minConfidence(), profile.maxP95LatencyMillis(),
                profile.maxCostPerTask(), profile.allowedProviders(), profile.minSamples(), profile.evidenceTtl(), profile.policyTtl());
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(success(first, "0.00004"));
        assertThrows(Unavailable.class, () -> executor(2).execute(new Request("", "structured", mapper.readTree("{\"type\":\"json_object\"}"), List.of(), false)));
        assertEquals(1, requests.size());
    }

    @Test void expiredFallbackAfterFirstAttemptIsNotUsed() throws Exception {
        activate(policy("v1", List.of(first, fallback), null));
        replies.add(new Reply(503, "{}"));
        var attemptClock = new Clock() {
            private final AtomicInteger reads = new AtomicInteger();
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return reads.incrementAndGet() <= 3 ? now : now.plusSeconds(600); }
        };
        var executor = new PolicyExecutor(policies, () -> profile, () -> catalog, client, attemptClock, audits::add, 2);
        assertEquals("STALE_OR_INELIGIBLE_POLICY", assertThrows(Unavailable.class, () -> executor.execute(request())).reason());
        assertEquals(1, requests.size());
    }

    @Test void billedDecimalPrecisionIsNotRoundedThroughDouble() throws Exception {
        activate(policy("v1", List.of(first), null));
        replies.add(success(first, "0.00120000000000000001"));
        assertEquals("BUDGET_VIOLATION", assertThrows(Unavailable.class, () -> executor(1).execute(request())).reason());
        assertTrue(policies.revoked("v1"));
        assertEquals(0, new BigDecimal("0.00120000000000000001").compareTo(audits.getFirst().actualCost()));
    }

    @Test void corruptedPricesAndAmbiguousEndpointsAreIneligible() throws Exception {
        Endpoint missing = new Endpoint(first.modelId(), first.provider(), first.supportedParameters(), 4096, 512, null, true);
        assertFalse(PolicyEligibility.eligible(qualified(missing), profile, catalog, now));
        Endpoint negativeCache = new Endpoint(first.modelId(), first.provider(), first.supportedParameters(), 4096, 512,
                new PriceRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), null), true);
        assertFalse(PolicyEligibility.eligible(qualified(negativeCache), profile, catalog, now));
        var duplicate = new CatalogSnapshot(catalog.version(), catalog.fetchedAt(), catalog.expiresAt(), List.of(first, first), catalog.rawHash());
        assertFalse(PolicyEligibility.eligible(qualified(first), profile, duplicate, now));
    }

    @Test void jsonAndNamedFunctionResponsesArePreservedWithoutExecutingTools() throws Exception {
        profile = new TaskProfile("cpq", "1", Set.of("tools", "parallel_tool_calls", "response_format"), 1000, 100,
                new BigDecimal("0.8"), new BigDecimal("0.9"), 5000, new BigDecimal("0.01"), Set.of("provider-a", "provider-b"),
                3, Duration.ofHours(1), Duration.ofMinutes(10));
        activate(policy("v1", List.of(first), null));
        JsonNode tool = mapper.readTree("{\"type\":\"function\",\"function\":{\"name\":\"price\",\"parameters\":{\"type\":\"object\"}}}");
        String body = success(first, "0.00004").body().replace("\"content\":\"safe answer\"",
                "\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"price\",\"arguments\":\"{}\"}}]")
                .replace("\"finish_reason\":\"stop\"", "\"finish_reason\":\"tool_calls\"");
        replies.add(new Reply(200, body));
        Completion result = executor(1).execute(new Request("", "price", null, List.of(tool), false));
        assertEquals(1, result.toolCalls().size());
        assertFalse(requests.getFirst().path("parallel_tool_calls").asBoolean(true));
    }

    private PolicyExecutor executor(int attempts) {
        return new PolicyExecutor(policies, () -> profile, () -> catalog, client, Clock.fixed(now, ZoneOffset.UTC), audits::add, attempts);
    }
    private void activate(SelectionPolicy policy) throws Exception { policies.saveProposal(policy); policies.activate(policy.version(), this::valid); }
    private boolean valid(SelectionPolicy policy) { return PolicyEligibility.valid(policy, profile, catalog, now); }
    private Request request() { return new Request("assist", "sensitive synthetic payload", null, List.of(), false); }
    private static Endpoint endpoint(String model, String provider) {
        return new Endpoint(model, provider, Set.of("tools", "parallel_tool_calls", "response_format"), 4096, 512,
                new PriceRates(new BigDecimal("0.000001"), new BigDecimal("0.000002"), BigDecimal.ZERO, null, null), true);
    }
    private TaskProfile profile(BigDecimal budget) {
        return new TaskProfile("cpq", "1", Set.of(), 1000, 100, new BigDecimal("0.8"), new BigDecimal("0.9"),
                5000, budget, Set.of("provider-a", "provider-b"), 3, Duration.ofHours(1), Duration.ofMinutes(10));
    }
    private QualifiedCandidate qualified(Endpoint endpoint) {
        return new QualifiedCandidate(endpoint, new BigDecimal("0.0001"), new BigDecimal("0.95"), 10, new BigDecimal("0.99"), "bench-1");
    }
    private SelectionPolicy policy(String version, List<Endpoint> endpoints, String previous) {
        return new SelectionPolicy(version, profile.id(), profile.version(), catalog.version(), endpoints.stream().map(this::qualified).toList(),
                now, now.plusSeconds(300), previous);
    }
    private Reply success(Endpoint endpoint, String cost) {
        return new Reply(200, "{\"model\":\"" + endpoint.modelId() + "\",\"provider\":\"" + endpoint.provider()
                + "\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"safe answer\"}}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":15,\"cost\":" + cost + "}}");
    }
    private record Reply(int status, String body) { }
}
