package com.opencrm.ai.selection.evaluation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class SelectionEvaluationCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EVALUATOR = "typesafe/jev-1.13-20260917";
    @TempDir Path output;
    private HttpServer server;
    private URI base;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger completions = new AtomicInteger();
    private boolean plainToolJson;
    private boolean forbiddenProvider;

    @BeforeEach void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            String content;
            if (path.equals("/api/v1/models")) content = "{\"data\":[{\"id\":\"vendor/model\"}]}";
            else if (path.endsWith("/endpoints")) content = """
                    {"data":{"id":"vendor/model","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
                    "endpoints":[{"tag":"provider-a","provider_name":"Provider A","status":0,
                      "context_length":10000,"max_completion_tokens":2048,
                      "supported_parameters":["tools","parallel_tool_calls","response_format"],
                      "pricing":{"prompt":"0.000001","completion":"0.000002","request":"0"}}]}}
                    """.replace("provider-a", forbiddenProvider ? "forbidden" : "provider-a");
            else {
                JsonNode request = JSON.readTree(exchange.getRequestBody());
                if (path.equals("/chat")) {
                    completions.incrementAndGet();
                    assertThat(request.path("provider").path("only").get(0).asText()).isEqualTo("provider-a");
                    assertThat(request.path("provider").path("allow_fallbacks").asBoolean()).isFalse();
                    String prompt = request.path("messages").get(1).path("content").asText();
                    ObjectNode root = JSON.createObjectNode().put("model", "vendor/model").put("provider", "Provider A");
                    ObjectNode choice = root.putArray("choices").addObject();
                    ObjectNode message = choice.putObject("message");
                    if (request.has("tools") && !plainToolJson) {
                        assertThat(request.path("tools").get(0).path("function").path("name").asText()).isEqualTo("getQuote");
                        choice.put("finish_reason", "tool_calls");
                        message.putArray("tool_calls").addObject().put("id", "tool-1").put("type", "function")
                                .putObject("function").put("name", "getQuote")
                                .put("arguments", "{\"quoteId\":" + (prompt.contains("73") ? 73 : 42) + "}");
                    } else {
                        choice.put("finish_reason", "stop");
                        String result = request.has("tools") ? "{\"tool\":\"getQuote\",\"arguments\":{\"quoteId\":42}}" :
                                prompt.contains("200") ? "{\"marginPercent\":40,\"requiresReview\":false}" :
                                        "{\"marginPercent\":25,\"requiresReview\":true}";
                        message.put("content", result);
                    }
                    root.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 30)
                            .put("cost", new BigDecimal("0.00016"));
                    content = root.toString();
                } else if (path.equals("/decisions")) {
                    ObjectNode root = JSON.createObjectNode().put("model", EVALUATOR).put("provider", "TypeSafe");
                    root.putObject("usage").put("cost", new BigDecimal("0.0005"));
                    ObjectNode answers = root.putObject("answers");
                    request.path("questions").fieldNames().forEachRemaining(id ->
                            answers.putObject(id).put("type", "choice").put("choice", "suitable").put("confidence", new BigDecimal("0.99"))
                                    .putObject("probabilities").put("suitable", new BigDecimal("0.99")).put("unsuitable", new BigDecimal("0.01")));
                    content = root.toString();
                } else throw new AssertionError("Unexpected path " + path);
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void close() { server.stop(0); }

    private SelectionEvaluationCli.Config config(String suite, int calls) {
        var required = "tools".equals(suite) ? Set.of("tools", "parallel_tool_calls") : Set.of("response_format");
        TaskProfile profile = new TaskProfile("cpq-" + suite, "v1", required, 2048, 256,
                new BigDecimal("0.9"), new BigDecimal("0.9"), 5000, new BigDecimal("0.1"),
                Set.of("provider-a"), 2, Duration.ofDays(1), Duration.ofHours(1));
        return new SelectionEvaluationCli.Config(profile, base.resolve("/api/v1"), Duration.ofHours(1), Set.of("vendor/model"),
                new JevDecisionAdapter.Config(base.resolve("/decisions"), EVALUATOR, "rubric-v1", Set.of("typesafe"),
                        Duration.ofSeconds(2), 0, new BigDecimal("0.002"), Set.of("TypeSafe")),
                base.resolve("/chat"), Duration.ofSeconds(2), suite,
                new EvaluationHarness.Settings("synthetic-v1", "Correct arithmetic or exact safe read-only tool arguments", 1, 1),
                calls, BigDecimal.ONE, output.toString());
    }

    @Test void defaultModeValidatesWithoutCredentialsNetworkOrActivation() throws Exception {
        var result = SelectionEvaluationCli.run(config("advice", 10), false, null, Clock.systemUTC());
        assertThat(result.mode()).isEqualTo("dry-run");
        assertThat(requests.get()).isZero();
        assertThat(result.selection()).isNull();
        assertThat(Path.of(result.directory()).resolve("validated-config.json")).exists();
        assertThat(Path.of(result.directory()).resolve("proposed-policy.json")).doesNotExist();
    }

    @Test void liveAdviceTraversesCatalogChatJevAndWritesReplayableProposal() throws Exception {
        var result = SelectionEvaluationCli.run(config("advice", 10), true, "test-credential", Clock.systemUTC());
        assertThat(result.selection().proposedPolicy()).isNotNull();
        assertThat(result.selection().complete()).isTrue();
        assertThat(result.calls()).isEqualTo(5); // discovery, endpoints, 2 samples, evaluator
        assertThat(requests.get()).isEqualTo(5);
        assertThat(result.spend()).isEqualByComparingTo("0.00082");
        Path directory = Path.of(result.directory());
        assertThat(directory.resolve("proposed-policy.json")).exists();
        assertThat(directory.resolve("evidence.json")).exists();
        var replay = new EvaluationReplayStore().load(directory.resolve("evaluation.json"));
        assertThat(replay.candidates().getFirst().evidence().successes()).isEqualTo(2);
        assertThat(Files.readString(directory.resolve("evaluation.json"))).doesNotContain("test-credential");
        assertThat(directory.resolve("active-policy.json")).doesNotExist();
    }

    @Test void genuineToolProbeRequiresReturnedFunctionCalls() throws Exception {
        var good = SelectionEvaluationCli.run(config("tools", 10), true, "test", Clock.systemUTC());
        assertThat(good.selection().proposedPolicy()).isNotNull();
        assertThat(completions.get()).isEqualTo(2);
        plainToolJson = true;
        var invalid = SelectionEvaluationCli.run(config("tools", 10), true, "test", Clock.systemUTC());
        assertThat(invalid.selection().proposedPolicy()).isNull();
        var replay = new EvaluationReplayStore().load(Path.of(invalid.directory()).resolve("evaluation.json"));
        assertThat(replay.candidates().getFirst().evidence().capabilityProbePassed()).isFalse();
        assertThat(replay.candidates().getFirst().evidence().successes()).isZero();
    }

    @Test void hardProviderFilterStopsGenerationBeforePaidCalls() throws Exception {
        forbiddenProvider = true;
        var result = SelectionEvaluationCli.run(config("advice", 10), true, "test", Clock.systemUTC());
        assertThat(completions.get()).isZero();
        assertThat(requests.get()).isEqualTo(2);
        assertThat(result.selection().proposedPolicy()).isNull();
    }

    @Test void partialBudgetRunWritesNoProposedWinner() throws Exception {
        var result = SelectionEvaluationCli.run(config("advice", 3), true, "test", Clock.systemUTC());
        assertThat(result.selection().complete()).isFalse();
        assertThat(result.calls()).isEqualTo(3);
        assertThat(requests.get()).isEqualTo(3);
        assertThat(Path.of(result.directory()).resolve("proposed-policy.json")).doesNotExist();
    }

    @Test void catalogTildeModelIdentifiersValidateButPathTraversalDoesNot() throws Exception {
        var c = config("advice", 10);
        java.util.function.Function<String, SelectionEvaluationCli.Config> withModel = id ->
                new SelectionEvaluationCli.Config(c.profile(), c.catalogBaseUri(), c.catalogTtl(), Set.of(id),
                        c.evaluator(), c.completionsUri(), c.completionsTimeout(), c.fixtureSuite(), c.settings(),
                        c.maxCalls(), c.spendCap(), c.outputDirectory());
        var outcome = SelectionEvaluationCli.run(withModel.apply("~deepseek/deepseek-pro-latest"),
                false, null, Clock.systemUTC());
        assertThat(outcome.mode()).isEqualTo("dry-run");
        assertThat(requests.get()).isZero();
        for (String invalid : List.of("../model", "vendor/..", "vendor/model/extra", "vendor/model?key=value"))
            assertThatThrownBy(() -> withModel.apply(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
