package com.opencrm.ai.selection.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.selection.SelectionTypes.CatalogSnapshot;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class OpenRouterCatalogClientTest {
    @TempDir Path snapshots;
    private final ObjectMapper json = new ObjectMapper();
    private final Instant now = Instant.parse("2026-09-19T12:00:00Z");
    private HttpServer server;
    private String models;
    private ObjectNode details;
    private int detailsStatus;
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void serveReadOnlyFixtures() throws Exception {
        models = "{\"data\":[{\"id\":\"vendor/model\"}]}";
        details = (ObjectNode) json.readTree("""
                {"data":{"id":"vendor/model","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
                "endpoints":[{"tag":"provider-a","provider_name":"Provider A","model_id":"vendor/model","status":0,
                  "context_length":8192,"max_completion_tokens":2048,"max_prompt_tokens":8192,
                  "supported_parameters":["tools","response_format"],
                  "pricing":{"prompt":"0.000001","completion":"0.000002","request":"0","image":"0"}}]}}
                """);
        detailsStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean endpoints = exchange.getRequestURI().getPath().endsWith("/endpoints");
            byte[] bytes = (endpoints ? details.toString() : models).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(endpoints ? detailsStatus : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void normalizesProviderSpecificPricesAndPersistsExactRawEvidence() throws Exception {
        CatalogSnapshot snapshot = client().fetch("test-key");
        assertThat(snapshot.endpoints()).hasSize(1);
        assertThat(snapshot.endpoints().getFirst().provider()).isEqualTo("provider-a");
        assertThat(snapshot.endpoints().getFirst().responseProvider()).isEqualTo("Provider A");
        assertThat(snapshot.endpoints().getFirst().prices().prompt()).isEqualByComparingTo("0.000001");
        assertThat(snapshot.endpoints().getFirst().prices().request()).isZero();
        assertThat(snapshot.fetchedAt()).isEqualTo(now);
        assertThat(snapshot.expiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(snapshot.rawHash()).matches("[a-f0-9]{64}");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        var normalized = json.readTree(Files.readString(snapshots.resolve(snapshot.version() + ".json")));
        assertThat(normalized.size()).isEqualTo(5);
        assertThat(normalized.has("rawResponses")).isFalse();
        CatalogSnapshot restored = json.copy().findAndRegisterModules().readValue(
                Files.readString(snapshots.resolve(snapshot.version() + ".json")), CatalogSnapshot.class);
        assertThat(restored).usingRecursiveComparison()
                .withComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class).isEqualTo(snapshot);
        var persisted = json.readTree(Files.readString(snapshots.resolve(snapshot.version() + ".evidence.json")));
        assertThat(persisted.path("rawResponses").path("models").asText()).isEqualTo(models);
        assertThat(persisted.toString()).doesNotContain("test-key");
        assertThat(persisted.path("rawHash").asText()).isEqualTo(snapshot.rawHash());
    }

    @Test
    void explicitShortlistBoundsEndpointFetchesAndRecordsCoverage() throws Exception {
        models = "{\"data\":[{\"id\":\"vendor/model\"},{\"id\":\"other/model\"}]}";
        CatalogSnapshot snapshot = client().fetch(null, Set.of("vendor/model"));
        assertThat(snapshot.endpoints()).hasSize(1);
        var evidence = json.readTree(Files.readString(snapshots.resolve(snapshot.version() + ".evidence.json")));
        assertThat(evidence.path("coverage").asText()).isEqualTo("explicit_shortlist");
        assertThat(evidence.path("discoveredModelCount").asInt()).isEqualTo(2);
        assertThat(evidence.path("rawResponses").size()).isEqualTo(2);
        assertThat(evidence.path("excluded").get(0).path("model").asText()).isEqualTo("other/model");
        assertThatThrownBy(() -> client().fetch(null, Set.of("missing/model")))
                .isInstanceOf(IOException.class).hasMessageContaining("undiscovered");
        assertThatThrownBy(() -> client().fetch(null, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unselectedTildeModelDoesNotAbortCompleteDiscovery() throws Exception {
        models = "{\"data\":[{\"id\":\"vendor/model\"},{\"id\":\"~deepseek/deepseek-pro-latest\"}]}";
        CatalogSnapshot snapshot = client().fetch(null, Set.of("vendor/model"));
        assertThat(snapshot.endpoints()).hasSize(1);
        var evidence = json.readTree(Files.readString(snapshots.resolve(snapshot.version() + ".evidence.json")));
        assertThat(evidence.path("discoveredModelCount").asInt()).isEqualTo(2);
        assertThat(evidence.path("excluded").get(0).path("model").asText()).isEqualTo("~deepseek/deepseek-pro-latest");
    }

    @Test
    void missingNegativeExtraPricesAndUnknownCapabilitiesAreExcluded() throws Exception {
        ObjectNode endpoint = endpoint();
        ObjectNode prices = (ObjectNode) endpoint.path("pricing");
        prices.remove("request");
        assertThat(client().fetch(null).endpoints()).isEmpty();
        prices.put("request", "0").put("prompt", "-1");
        assertThat(client().fetch(null).endpoints()).isEmpty();
        prices.put("prompt", "0").put("web_search", "0.005");
        assertThat(client().fetch(null).endpoints()).isEmpty();
        prices.remove("web_search");
        endpoint.remove("supported_parameters");
        assertThat(client().fetch(null).endpoints()).isEmpty();
    }

    @Test
    void rejectsUnsupportedModalitiesAndUnavailableEndpoints() throws Exception {
        ((ObjectNode) details.path("data").path("architecture")).putArray("input_modalities").add("text").add("image");
        assertThat(client().fetch(null).endpoints()).isEmpty();
        ((ObjectNode) details.path("data").path("architecture")).putArray("input_modalities").add("text");
        endpoint().put("status", -1);
        assertThat(client().fetch(null).endpoints()).isEmpty();
    }

    @Test
    void paginationMismatchDuplicateModelsAndEndpointFailuresCannotPublishPartialSnapshots() throws Exception {
        models = "{\"data\":[{\"id\":\"vendor/model\"}],\"has_more\":true}";
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("pagination");
        models = "{\"data\":[{\"id\":\"vendor/model\"}],\"total\":2}";
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("count");
        models = "{\"data\":[{\"id\":\"vendor/model\"}],\"links\":{\"next\":\"?cursor=next\"}}";
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("pagination");
        models = "{\"data\":[{\"id\":\"vendor/model\"}],\"meta\":{\"pagination\":{\"has_more\":true}}}";
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("pagination");
        models = "{\"data\":[{\"id\":\"vendor/model\"},{\"id\":\"vendor/model\"}]}";
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("duplicate");
        models = "{\"data\":[{\"id\":\"vendor/model\"}]}";
        detailsStatus = 503;
        assertThatThrownBy(() -> client().fetch(null)).isInstanceOf(IOException.class).hasMessageContaining("503");
        try (var files = Files.list(snapshots)) { assertThat(files.count()).isZero(); }
    }

    @Test
    void staleAndFutureSnapshotsAreIneligible() {
        assertThatThrownBy(() -> client().requireFresh(new CatalogSnapshot("old", now.minusSeconds(60), now, List.of(), "hash")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client().requireFresh(new CatalogSnapshot("future", now.plusSeconds(1), now.plusSeconds(60), List.of(), "hash")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ObjectNode endpoint() { return (ObjectNode) details.path("data").path("endpoints").get(0); }

    private OpenRouterCatalogClient client() {
        return new OpenRouterCatalogClient(HttpClient.newHttpClient(), json,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1"),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1), snapshots);
    }
}
