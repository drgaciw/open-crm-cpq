package com.opencrm.security.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client-credentials grant against a local stand-in for the Entra token endpoint. */
@DisplayName("ADR-016 D5: client-credentials service tokens (workload identity federation)")
class ClientCredentialsServiceTokenProviderTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private URI tokenUri;
    private final List<Map<String, String>> requests = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger issued = new AtomicInteger();
    private final AtomicReference<Response> next = new AtomicReference<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-23T10:00:00Z"));

    private record Response(int status, String body) {
    }

    @BeforeEach
    void startTokenEndpoint() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tenant-id/oauth2/v2.0/token", exchange -> {
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> fields = new LinkedHashMap<>();
            for (String pair : form.split("&")) {
                String[] kv = pair.split("=", 2);
                fields.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
            fields.put("#content-type", exchange.getRequestHeaders().getFirst("Content-Type"));
            requests.add(fields);
            Response response = next.get() != null ? next.get() : new Response(200,
                    "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"access_token\":\"token-" + issued.incrementAndGet()
                            + "\"}");
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        tokenUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/tenant-id/oauth2/v2.0/token");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ClientCredentialsServiceTokenProvider federated(Path file) {
        return ClientCredentialsServiceTokenProvider.federated(tokenUri, "quote-service-client-id",
                "api://opencrm-api/.default", file, FIVE_MINUTES, TIMEOUT, clock);
    }

    private Path assertionFile(String content) throws IOException {
        Path file = tempDir.resolve("azure-identity-token");
        Files.writeString(file, content + "\n");
        return file;
    }

    @Test
    @DisplayName("workload identity: the projected token is the jwt-bearer client assertion")
    void federatedRequest() throws IOException {
        assertThat(federated(assertionFile("projected-sa-token")).token()).contains("token-1");

        assertThat(requests).hasSize(1);
        Map<String, String> request = requests.getFirst();
        assertThat(request).containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", "quote-service-client-id")
                .containsEntry("scope", "api://opencrm-api/.default")
                .containsEntry("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .containsEntry("client_assertion", "projected-sa-token")
                .doesNotContainKey("client_secret");
        assertThat(request.get("#content-type")).startsWith("application/x-www-form-urlencoded");
    }

    @Test
    @DisplayName("the token is cached until five minutes before expiry, then refreshed with a re-read assertion")
    void cachesAndRefreshes() throws IOException {
        Path file = assertionFile("assertion-1");
        ClientCredentialsServiceTokenProvider provider = federated(file);

        assertThat(provider.token()).contains("token-1");
        clock.advance(Duration.ofMinutes(50));
        assertThat(provider.token()).as("still cached").contains("token-1");
        assertThat(requests).hasSize(1);

        Files.writeString(file, "assertion-2");
        clock.advance(Duration.ofMinutes(5));
        assertThat(provider.token()).as("inside the refresh window").contains("token-2");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).containsEntry("client_assertion", "assertion-2");
    }

    @Test
    @DisplayName("a failed refresh keeps the cached token until it expires, then fails")
    void refreshFailure() throws IOException {
        ClientCredentialsServiceTokenProvider provider = federated(assertionFile("a"));
        assertThat(provider.token()).contains("token-1");

        next.set(new Response(503, "{\"error\":\"temporarily_unavailable\"}"));
        clock.advance(Duration.ofMinutes(56));
        assertThat(provider.token()).as("refresh failed, token still valid").contains("token-1");

        clock.advance(Duration.ofMinutes(4));
        assertThatThrownBy(provider::token)
                .isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("HTTP 503")
                .hasMessageContaining("temporarily_unavailable");
    }

    @Test
    @DisplayName("a refusal reports the OAuth error code only, never the description or the credential")
    void refusal() {
        next.set(new Response(401, "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: secret "
                + "s3cr3t-value is wrong. Trace ID: 123\"}"));
        ClientCredentialsServiceTokenProvider provider = ClientCredentialsServiceTokenProvider.secret(tokenUri,
                "client-id", "api://opencrm-api/.default", "s3cr3t-value", FIVE_MINUTES, TIMEOUT, clock);

        assertThatThrownBy(provider::token)
                .isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("(invalid_client)")
                .hasMessageNotContaining("s3cr3t-value")
                .hasMessageNotContaining("AADSTS");
        assertThat(requests.getFirst()).containsEntry("client_secret", "s3cr3t-value")
                .doesNotContainKey("client_assertion");
    }

    @Test
    @DisplayName("malformed responses and an unreadable assertion file are ServiceTokenExceptions")
    void malformed() throws IOException {
        next.set(new Response(200, "{\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        assertThatThrownBy(federated(assertionFile("a"))::token).isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("no access_token");

        next.set(new Response(200, "{\"token_type\":\"mac\",\"expires_in\":3600,\"access_token\":\"x\"}"));
        assertThatThrownBy(federated(assertionFile("a"))::token).isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("token_type");

        next.set(null);
        assertThatThrownBy(federated(tempDir.resolve("missing"))::token).isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("AZURE_FEDERATED_TOKEN_FILE");
    }

    @Test
    @DisplayName("the token endpoint must be https, except on localhost")
    void httpsOnly() {
        assertThatThrownBy(() -> ClientCredentialsServiceTokenProvider.federated(
                URI.create("http://login.example.com/t/oauth2/v2.0/token"), "c", "s", tempDir.resolve("f"),
                FIVE_MINUTES, TIMEOUT, clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    /** A clock the test moves forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
