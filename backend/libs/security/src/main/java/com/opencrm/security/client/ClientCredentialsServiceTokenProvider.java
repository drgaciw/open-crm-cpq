package com.opencrm.security.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth 2.0 client-credentials grant (RFC 6749 section 4.4) against the Entra token endpoint, without
 * {@code spring-security-oauth2-client}, which ADR-016 decision 8 keeps in the gateway.
 *
 * <p>The service authenticates in one of two ways:
 * <ul>
 *   <li><strong>workload identity federation</strong> (AKS, the production path): the projected service-account token
 *       in {@code AZURE_FEDERATED_TOKEN_FILE} is sent as a {@code jwt-bearer} client assertion (RFC 7523). The file is
 *       re-read for every request because kubelet rotates it;</li>
 *   <li><strong>client secret</strong>: local tests against a real tenant only. {@link ServiceTokenProviders} refuses it
 *       when the production marker is set.</li>
 * </ul>
 *
 * <p>Tokens are cached and replaced {@code refreshBeforeExpiry} before they expire (at most half their lifetime). If a
 * refresh fails while the cached token is still valid, the cached token is used and a WARN is logged. Secrets and
 * assertions never appear in exceptions or logs.
 */
@Slf4j
public final class ClientCredentialsServiceTokenProvider implements ServiceTokenProvider {

    static final String JWT_BEARER_ASSERTION = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };
    private static final Pattern OAUTH_ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]{1,100})\"");
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final URI tokenUri;
    private final String clientId;
    private final String scope;
    private final Path federatedTokenFile;
    private final String clientSecret;
    private final Duration refreshBeforeExpiry;
    private final Clock clock;
    private final RestClient http;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile CachedToken cached;

    private ClientCredentialsServiceTokenProvider(URI tokenUri, String clientId, String scope, Path federatedTokenFile,
                                                  String clientSecret, Duration refreshBeforeExpiry, Duration timeout,
                                                  Clock clock) {
        this.tokenUri = requireSafeTokenUri(tokenUri);
        this.clientId = requireText(clientId, "client id");
        this.scope = requireText(scope, "scope");
        this.federatedTokenFile = federatedTokenFile;
        this.clientSecret = clientSecret;
        this.refreshBeforeExpiry = refreshBeforeExpiry;
        this.clock = clock;
        // java.net.http rather than HttpURLConnection: it returns the body of a 401 refusal, which carries the error code.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.http = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Workload identity federation: the contents of {@code federatedTokenFile} are the client assertion. */
    public static ClientCredentialsServiceTokenProvider federated(URI tokenUri, String clientId, String scope,
                                                                  Path federatedTokenFile, Duration refreshBeforeExpiry,
                                                                  Duration timeout, Clock clock) {
        if (federatedTokenFile == null) {
            throw new IllegalArgumentException("federatedTokenFile is required");
        }
        return new ClientCredentialsServiceTokenProvider(tokenUri, clientId, scope, federatedTokenFile, null,
                refreshBeforeExpiry, timeout, clock);
    }

    /** Client secret: local tests against a real tenant only. */
    public static ClientCredentialsServiceTokenProvider secret(URI tokenUri, String clientId, String scope,
                                                               String clientSecret, Duration refreshBeforeExpiry,
                                                               Duration timeout, Clock clock) {
        return new ClientCredentialsServiceTokenProvider(tokenUri, clientId, scope, null,
                requireText(clientSecret, "client secret"), refreshBeforeExpiry, timeout, clock);
    }

    @Override
    public Optional<String> token() {
        CachedToken current = cached;
        if (current != null && clock.instant().isBefore(current.refreshAt())) {
            return Optional.of(current.value());
        }
        lock.lock();
        try {
            current = cached;
            Instant now = clock.instant();
            if (current != null && now.isBefore(current.refreshAt())) {
                return Optional.of(current.value());
            }
            try {
                CachedToken fresh = request(now);
                cached = fresh;
                return Optional.of(fresh.value());
            } catch (ServiceTokenException e) {
                if (current != null && now.isBefore(current.expiresAt())) {
                    log.warn("Service token refresh failed; using the cached token until it expires at {}: {}",
                            current.expiresAt(), e.getMessage());
                    return Optional.of(current.value());
                }
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /** The token endpoint, for diagnostics. */
    public URI tokenUri() {
        return tokenUri;
    }

    /** The client id, for diagnostics. */
    public String clientId() {
        return clientId;
    }

    private CachedToken request(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("scope", scope);
        if (federatedTokenFile != null) {
            form.add("client_assertion_type", JWT_BEARER_ASSERTION);
            form.add("client_assertion", readAssertion());
        } else {
            form.add("client_secret", clientSecret);
        }
        Map<String, Object> body;
        try {
            body = http.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException e) {
            throw new ServiceTokenException("Token endpoint " + tokenUri + " refused client " + clientId + ": HTTP "
                    + e.getStatusCode().value() + oauthError(e.getResponseBodyAsString(StandardCharsets.UTF_8)), e);
        } catch (RestClientException e) {
            throw new ServiceTokenException("Token endpoint " + tokenUri + " is unreachable: " + e.getMessage(), e);
        }
        return parse(body, now);
    }

    private CachedToken parse(Map<String, Object> body, Instant now) {
        Object accessToken = body == null ? null : body.get("access_token");
        if (!(accessToken instanceof String value) || value.isBlank()) {
            throw new ServiceTokenException("Token endpoint " + tokenUri + " returned no access_token");
        }
        Object tokenType = body.get("token_type");
        if (tokenType != null && !"bearer".equalsIgnoreCase(tokenType.toString())) {
            throw new ServiceTokenException("Token endpoint " + tokenUri + " returned token_type " + tokenType
                    + ", expected Bearer");
        }
        long lifetime = seconds(body.get("expires_in"));
        Instant expiresAt = now.plusSeconds(lifetime);
        Duration lead = refreshBeforeExpiry.compareTo(Duration.ofSeconds(lifetime / 2)) < 0
                ? refreshBeforeExpiry : Duration.ofSeconds(lifetime / 2);
        return new CachedToken(value, expiresAt, expiresAt.minus(lead));
    }

    private String readAssertion() {
        try {
            String assertion = Files.readString(federatedTokenFile, StandardCharsets.UTF_8).trim();
            if (assertion.isEmpty()) {
                throw new ServiceTokenException("The federated token file " + federatedTokenFile + " is empty");
            }
            return assertion;
        } catch (IOException e) {
            throw new ServiceTokenException("Cannot read the federated token file " + federatedTokenFile
                    + " (AZURE_FEDERATED_TOKEN_FILE)", e);
        }
    }

    /** {@code expires_in} as seconds; Entra sends a number, some servers a string. Absent means 5 minutes. */
    private static long seconds(Object expiresIn) {
        if (expiresIn == null) {
            return 300;
        }
        try {
            long value = expiresIn instanceof Number number ? number.longValue() : Long.parseLong(expiresIn.toString());
            if (value <= 0) {
                throw new ServiceTokenException("Token endpoint returned expires_in " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ServiceTokenException("Token endpoint returned a non-numeric expires_in", e);
        }
    }

    /** The OAuth {@code error} code of a refusal, never the description (it can echo request details). */
    private static String oauthError(String responseBody) {
        Matcher matcher = OAUTH_ERROR.matcher(responseBody == null ? "" : responseBody);
        return matcher.find() ? " (" + matcher.group(1) + ")" : "";
    }

    private static URI requireSafeTokenUri(URI uri) {
        if (uri == null || uri.getHost() == null) {
            throw new IllegalArgumentException("The token endpoint must be an absolute URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean loopback = LOOPBACK.contains(uri.getHost().toLowerCase(Locale.ROOT));
        if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
            throw new IllegalArgumentException("The token endpoint must use https (plain http only on localhost): "
                    + uri);
        }
        return uri;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + name + " is required");
        }
        return value.trim();
    }

    private record CachedToken(String value, Instant expiresAt, Instant refreshAt) {
        @Override
        public String toString() {
            return "CachedToken[expiresAt=" + expiresAt + "]";
        }
    }
}
