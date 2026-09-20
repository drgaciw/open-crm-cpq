package com.opencrm.ai.selection.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.selection.SelectionTypes.CatalogSnapshot;
import com.opencrm.ai.selection.SelectionTypes.Endpoint;
import com.opencrm.ai.selection.SelectionTypes.PriceRates;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Read-only OpenRouter catalog ingestion. A partial fetch never becomes a selectable catalog. */
public final class OpenRouterCatalogClient {
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
    private static final Set<String> PRICE_FIELDS = Set.of("prompt", "completion", "request",
            "input_cache_read", "input_cache_write");
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI base;
    private final Clock clock;
    private final Duration ttl;
    private final Path directory;

    public OpenRouterCatalogClient(HttpClient http, ObjectMapper json, URI baseApiUri,
            Clock clock, Duration ttl, Path snapshotDirectory) {
        boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(baseApiUri.getHost());
        if (!("https".equals(baseApiUri.getScheme()) || (loopback && "http".equals(baseApiUri.getScheme())))
                || baseApiUri.getUserInfo() != null || baseApiUri.getQuery() != null || baseApiUri.getFragment() != null
                || http.followRedirects() != HttpClient.Redirect.NEVER || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Invalid catalog transport or lifetime");
        }
        this.http = http;
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.base = URI.create(baseApiUri.toString().replaceAll("/+$", "") + "/");
        this.clock = clock; this.ttl = ttl; this.directory = snapshotDirectory;
    }

    public CatalogSnapshot fetch(String apiKey) throws IOException, InterruptedException {
        return fetchCatalog(apiKey, null);
    }

    /** Explicit shortlist for bounded benchmark runs; discovery remains complete and auditable. */
    public CatalogSnapshot fetch(String apiKey, Set<String> shortlistedModelIds) throws IOException, InterruptedException {
        if (shortlistedModelIds == null || shortlistedModelIds.isEmpty() || shortlistedModelIds.size() > 50) {
            throw new IllegalArgumentException("An explicit shortlist must contain 1 to 50 model IDs");
        }
        return fetchCatalog(apiKey, Set.copyOf(shortlistedModelIds));
    }

    private CatalogSnapshot fetchCatalog(String apiKey, Set<String> shortlist) throws IOException, InterruptedException {
        Instant fetchedAt = clock.instant();
        ObjectNode raw = json.createObjectNode();
        JsonNode models = get("models", apiKey, raw);
        JsonNode entries = array(models, "data");
        checkComplete(models, entries.size());
        Set<String> ids = new TreeSet<>();
        for (JsonNode model : entries) {
            String id = text(model, "id");
            if (!id.matches("[A-Za-z0-9._:~-]+/[A-Za-z0-9._:~-]+")
                    || List.of(id.split("/")).stream().anyMatch(part -> part.equals(".") || part.equals("..")) || !ids.add(id)) {
                throw new IOException("Invalid or duplicate model identifier");
            }
        }
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        ArrayNode excluded = json.createArrayNode();
        if (shortlist != null && !ids.containsAll(shortlist)) throw new IOException("Shortlist contains undiscovered model IDs");
        Set<String> selected = shortlist == null ? ids : new TreeSet<>(shortlist);
        for (String id : ids) {
            if (!selected.contains(id)) excluded.addObject().put("model", id).put("reason", "Outside explicit shortlist");
        }
        for (String id : selected) {
            JsonNode response = get("models/" + id + "/endpoints", apiKey, raw);
            JsonNode data = response.path("data");
            if (!id.equals(text(data, "id"))) throw new IOException("Endpoint model identifier mismatch");
            JsonNode providers = array(data, "endpoints");
            checkComplete(response, providers.size());
            checkComplete(data, providers.size());
            boolean textOnly = textOnly(data.path("architecture"));
            for (JsonNode provider : providers) {
                try {
                    if (!textOnly) throw new IllegalArgumentException("Unsupported or unknown modalities");
                    Endpoint endpoint = normalize(id, provider);
                    if (!keys.add(endpoint.key())) throw new IOException("Ambiguous duplicate model/provider endpoint");
                    endpoints.add(endpoint);
                } catch (IllegalArgumentException invalid) {
                    excluded.addObject().put("model", id).put("provider", provider.path("tag").asText("unknown"))
                            .put("reason", invalid.getMessage());
                }
            }
        }
        String rawBody = json.writeValueAsString(raw);
        String hash = sha256(rawBody);
        String version = fetchedAt.toEpochMilli() + "-" + hash.substring(0, 16);
        CatalogSnapshot snapshot = new CatalogSnapshot(version, fetchedAt, fetchedAt.plus(ttl), endpoints, hash);
        requireFresh(snapshot); // A slow/incomplete refresh cannot publish already-expired evidence.
        ObjectNode persisted = json.createObjectNode();
        persisted.put("version", version).put("fetchedAt", fetchedAt.toString())
                .put("expiresAt", snapshot.expiresAt().toString()).put("rawHash", hash);
        persisted.set("endpoints", json.valueToTree(endpoints));
        ObjectNode evidence = persisted.deepCopy();
        evidence.put("source", base.toString());
        evidence.put("currency", "USD");
        evidence.put("tokenPriceUnit", "per_token");
        evidence.put("requestPriceUnit", "per_request");
        evidence.set("rawResponses", raw);
        evidence.set("excluded", excluded);
        evidence.put("coverage", shortlist == null ? "full_catalog" : "explicit_shortlist");
        evidence.put("discoveredModelCount", ids.size());
        evidence.set("selectedModelIds", json.valueToTree(selected));
        // Publish evidence first; the plain snapshot is the consumption/commit marker.
        persist(version + ".evidence", json.writeValueAsBytes(evidence));
        persist(version, json.writeValueAsBytes(persisted));
        return snapshot;
    }

    public void requireFresh(CatalogSnapshot snapshot) {
        Instant now = clock.instant();
        if (snapshot.fetchedAt().isAfter(now) || !snapshot.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("Catalog is stale or future-dated");
        }
    }

    private JsonNode get(String path, String apiKey, ObjectNode raw) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").GET();
        if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
        HttpResponse<java.io.InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Catalog request failed with HTTP " + response.statusCode());
            if (response.headers().allValues("Link").stream().anyMatch(value -> value.contains("next"))) {
                throw new IOException("Unsupported catalog pagination contract");
            }
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("Catalog response exceeds safe size; refusing truncation");
            String exact = new String(bytes, StandardCharsets.UTF_8);
            JsonNode parsed = json.readTree(exact);
            if (parsed == null || !parsed.isObject() || parsed.has("error")) throw new IOException("Invalid catalog response");
            raw.put(path, exact);
            return parsed;
        }
    }

    private Endpoint normalize(String id, JsonNode provider) {
        String tag = provider.path("tag").asText("");
        String responseProvider = provider.path("provider_name").asText("");
        if (tag.isBlank() || responseProvider.isBlank()
                || !provider.path("status").isIntegralNumber() || provider.path("status").intValue() != 0) {
            throw new IllegalArgumentException("Unknown provider route or unavailable endpoint");
        }
        if (provider.has("model_id") && !id.equals(provider.path("model_id").asText())) {
            throw new IllegalArgumentException("Provider model identifier mismatch");
        }
        int context = positiveInt(provider, "context_length");
        int completion = positiveInt(provider, "max_completion_tokens");
        if (provider.hasNonNull("max_prompt_tokens")) context = Math.min(context, positiveInt(provider, "max_prompt_tokens"));
        JsonNode parameters = provider.path("supported_parameters");
        if (!parameters.isArray()) throw new IllegalArgumentException("Unknown supported parameters");
        Set<String> supported = new HashSet<>();
        for (JsonNode parameter : parameters) {
            if (!parameter.isTextual() || parameter.asText().isBlank()) throw new IllegalArgumentException("Invalid parameter capability");
            supported.add(parameter.asText());
        }
        JsonNode pricing = provider.path("pricing");
        if (!pricing.isObject()) throw new IllegalArgumentException("Missing provider pricing");
        pricing.fields().forEachRemaining(field -> {
            if (!PRICE_FIELDS.contains(field.getKey()) && rate(field.getValue()).signum() != 0) {
                throw new IllegalArgumentException("Unsupported price dimension: " + field.getKey());
            }
        });
        return new Endpoint(id, tag, supported, context, completion,
                new PriceRates(rate(pricing.path("prompt")), rate(pricing.path("completion")), rate(pricing.path("request")),
                        optionalRate(pricing, "input_cache_read"), optionalRate(pricing, "input_cache_write")), true, responseProvider);
    }

    private static BigDecimal optionalRate(JsonNode pricing, String field) {
        return pricing.hasNonNull(field) ? rate(pricing.path(field)) : null;
    }

    private static BigDecimal rate(JsonNode node) {
        if (!(node.isTextual() || node.isNumber())) throw new IllegalArgumentException("Unknown price");
        try {
            BigDecimal rate = new BigDecimal(node.asText());
            if (rate.signum() < 0 || rate.precision() > 30 || Math.abs((long) rate.scale()) > 30) {
                throw new IllegalArgumentException("Invalid price");
            }
            return rate;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid price", invalid); }
    }

    private static int positiveInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new IllegalArgumentException("Unknown token limit: " + field);
        }
        return value.intValue();
    }

    private static boolean textOnly(JsonNode architecture) {
        for (String field : List.of("input_modalities", "output_modalities")) {
            JsonNode modalities = architecture.path(field);
            if (!modalities.isArray() || modalities.size() != 1 || !"text".equals(modalities.get(0).asText())) return false;
        }
        return true;
    }

    private static String text(JsonNode node, String field) throws IOException {
        if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) throw new IOException("Missing catalog " + field);
        return node.path(field).asText();
    }

    private static JsonNode array(JsonNode node, String field) throws IOException {
        if (!node.path(field).isArray()) throw new IOException("Missing catalog array " + field);
        return node.path(field);
    }

    private static void checkComplete(JsonNode node, int count) throws IOException {
        // These documented endpoints return all results with no cursor. Fail closed on a changed contract.
        for (JsonNode container : List.of(node, node.path("links"), node.path("meta"))) {
            for (String field : List.of("next", "next_page", "next_cursor", "cursor", "pagination", "page", "has_more")) {
                JsonNode value = container.path(field);
                boolean terminal = value.isMissingNode() || value.isNull()
                        || (value.isTextual() && value.asText().isBlank())
                        || (field.equals("has_more") && value.isBoolean() && !value.booleanValue());
                if (!terminal) throw new IOException("Unsupported catalog pagination contract");
            }
        }
        for (String field : List.of("total", "count", "total_count")) {
            if (node.has(field) && (!node.path(field).isIntegralNumber() || node.path(field).longValue() != count)) {
                throw new IOException("Incomplete catalog count");
            }
        }
    }

    private void persist(String version, byte[] data) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "catalog-", ".tmp");
        try {
            Files.write(temporary, data);
            Path target = directory.resolve(version + ".json");
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
