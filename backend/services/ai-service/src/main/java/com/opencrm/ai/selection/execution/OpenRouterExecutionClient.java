package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.execution.ExecutionTypes.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounded non-streaming HTTP transport. Redirects and implicit provider fallbacks are forbidden. */
public final class OpenRouterExecutionClient {
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private final URI endpoint;
    private final String key;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public OpenRouterExecutionClient(URI endpoint, String key, Duration timeout, ObjectMapper mapper) {
        if (key == null || key.isBlank() || key.contains("\n") || key.contains("\r"))
            throw new IllegalArgumentException("Missing or invalid OpenRouter credential");
        boolean local = List.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost());
        if ((!"https".equals(endpoint.getScheme()) && !(local && "http".equals(endpoint.getScheme())))
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0)
            throw new IllegalArgumentException("Invalid execution endpoint or timeout");
        this.endpoint = endpoint; this.key = key; this.timeout = timeout;
        this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Completion complete(Endpoint route, TaskProfile profile, Request request) throws TransportFailure {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", route.modelId());
            body.put("stream", false);
            body.put("max_tokens", profile.maxOutputTokens());
            var messages = body.putArray("messages");
            if (!request.system().isBlank()) messages.addObject().put("role", "system").put("content", request.system());
            messages.addObject().put("role", "user").put("content", request.user());
            if (request.responseFormat() != null) body.set("response_format", request.responseFormat());
            if (!request.tools().isEmpty()) {
                var tools = body.putArray("tools");
                request.tools().forEach(tools::add);
                body.put("parallel_tool_calls", false);
            }
            var provider = body.putObject("provider");
            provider.putArray("only").add(route.provider());
            provider.put("allow_fallbacks", false);
            provider.put("require_parameters", true);
            provider.put("data_collection", "deny");
            var maxPrice = provider.putObject("max_price");
            maxPrice.put("prompt", route.prices().prompt().multiply(MILLION));
            maxPrice.put("completion", route.prices().completion().multiply(MILLION));
            maxPrice.put("request", route.prices().request());
            body.putObject("usage").put("include", true);
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            HttpResponse<byte[]> response = client.send(httpRequest, ignored -> new LimitedBodySubscriber(2 * 1024 * 1024));
            if (response.statusCode() != 200) {
                int status = response.statusCode();
                throw new TransportFailure("HTTP_" + status, status == 429 || status == 502 || status == 503 || status == 504);
            }
            try { return parse(response.body(), route, request); }
            catch (IOException | IllegalArgumentException e) { throw new TransportFailure("INVALID_RESPONSE", false); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportFailure("INTERRUPTED", false);
        } catch (IOException e) {
            throw new TransportFailure("TRANSPORT_UNCERTAIN", true);
        } catch (IllegalArgumentException e) {
            throw new TransportFailure("INVALID_RESPONSE", false);
        }
    }

    private Completion parse(byte[] bytes, Endpoint route, Request request) throws IOException, TransportFailure {
        JsonNode root = mapper.readTree(bytes);
        if (root == null || root.has("error") || !root.path("choices").isArray() || root.path("choices").size() != 1)
            throw new TransportFailure("INVALID_RESPONSE", false);
        if (!root.path("model").asText().equals(route.modelId())) throw new TransportFailure("MODEL_MISMATCH", false);
        // OpenRouter response provider labels can differ in case; other aliases must be normalized in the catalog.
        String provider = root.path("provider").asText();
        if (!provider.equalsIgnoreCase(route.responseProvider())) throw new TransportFailure("PROVIDER_MISMATCH", false);
        JsonNode choice = root.path("choices").get(0);
        if (!List.of("stop", "tool_calls").contains(choice.path("finish_reason").asText()))
            throw new TransportFailure("INCOMPLETE_RESPONSE", false);
        JsonNode message = choice.path("message");
        List<JsonNode> calls = new ArrayList<>();
        JsonNode rawCalls = message.path("tool_calls");
        if (!rawCalls.isMissingNode() && !rawCalls.isNull()) {
            if (!rawCalls.isArray() || rawCalls.size() > 1) throw new TransportFailure("INVALID_TOOL_CALLS", false);
            for (JsonNode call : rawCalls) {
                String name = call.path("function").path("name").asText();
                if (!"function".equals(call.path("type").asText()) || call.path("id").asText().isBlank()
                        || request.tools().stream().noneMatch(t -> t.path("function").path("name").asText().equals(name)))
                    throw new TransportFailure("UNKNOWN_TOOL", false);
                JsonNode arguments = mapper.readTree(call.path("function").path("arguments").asText());
                if (arguments == null || !arguments.isObject()) throw new TransportFailure("INVALID_TOOL_ARGUMENTS", false);
                JsonNode schema = request.tools().stream().filter(t -> t.path("function").path("name").asText().equals(name))
                        .findFirst().orElseThrow().path("function").path("parameters");
                if (!JsonSchemaSubset.matches(schema, arguments)) throw new TransportFailure("INVALID_TOOL_ARGUMENTS", false);
                calls.add(call.deepCopy());
            }
        }
        String content = message.path("content").isTextual() ? message.path("content").asText() : "";
        if (content.isEmpty() && calls.isEmpty()) throw new TransportFailure("EMPTY_RESPONSE", false);
        if (request.responseFormat() != null && !content.isEmpty()) {
            JsonNode structured = mapper.readTree(content);
            if (structured == null || (!structured.isObject() && !structured.isArray()))
                throw new TransportFailure("INVALID_STRUCTURED_RESPONSE", false);
            if ("json_object".equals(request.responseFormat().path("type").asText()) && !structured.isObject())
                throw new TransportFailure("INVALID_STRUCTURED_RESPONSE", false);
            if ("json_schema".equals(request.responseFormat().path("type").asText())
                    && !JsonSchemaSubset.matches(request.responseFormat().path("json_schema").path("schema"), structured))
                throw new TransportFailure("INVALID_STRUCTURED_RESPONSE", false);
        }
        JsonNode usage = root.path("usage");
        long input = requiredLong(usage, "prompt_tokens"), output = requiredLong(usage, "completion_tokens");
        JsonNode details = usage.path("prompt_tokens_details");
        if (!details.isMissingNode() && !details.isObject()) throw new TransportFailure("INVALID_CACHE_USAGE", false);
        long cached = optionalLong(details, "cached_tokens");
        long written = optionalLong(details, "cache_write_tokens");
        if ((cached > 0 && route.prices().cacheRead() == null) || (written > 0 && route.prices().cacheWrite() == null))
            throw new TransportFailure("UNKNOWN_CACHE_PRICE", false);
        JsonNode cost = usage.path("cost");
        if (!cost.isNumber() || cost.decimalValue().signum() < 0) throw new TransportFailure("MISSING_BILLED_COST", false);
        return new Completion(content, calls, new Usage(input, output, cached, written, 1, cost.decimalValue()), root.path("model").asText(), provider);
    }

    private long optionalLong(JsonNode root, String name) throws TransportFailure {
        return root.has(name) ? requiredLong(root, name) : 0;
    }

    private long requiredLong(JsonNode root, String name) throws TransportFailure {
        JsonNode value = root.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new TransportFailure("MISSING_USAGE", false);
        return value.longValue();
    }

    /** One byte is a conservative token allowance for UTF-8 text; reserve extra protocol overhead. */
    public long inputAllowance(Request request) {
        try { return Math.addExact(256L, mapper.writeValueAsBytes(request).length); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid request"); }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBodySubscriber(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if ((long) bytes.size() + chunk.remaining() > limit) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Response limit exceeded")); return;
                }
                byte[] next = new byte[chunk.remaining()]; chunk.get(next); bytes.writeBytes(next);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
