package com.opencrm.gateway.security;

import com.opencrm.security.test.TestTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("ADR-016 D2: the gateway's Entra registration and its client authentication")
class GatewayLoginConfigurationTest {

    private static final String TENANT_BASE = TestTokens.ISSUER.substring(0, TestTokens.ISSUER.length() - "/v2.0".length());

    @TempDir
    Path tempDir;

    private static GatewayBffProperties.Login login() {
        GatewayBffProperties.Login login = new GatewayBffProperties.Login();
        login.setClientId("gateway-client");
        return login;
    }

    @Test
    @DisplayName("endpoints come from the tenant issuer; PKCE on; no user-info call; RP-initiated logout known")
    void registrationFromTenantIssuer() throws IOException {
        Path token = Files.writeString(tempDir.resolve("token"), "projected");
        ClientRegistration registration = GatewayLoginConfiguration.registration(login(), TestTokens.ISSUER,
                GatewayClientAuthentication.federated(token));

        assertThat(registration.getRegistrationId()).isEqualTo("entra");
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(registration.getClientSecret()).isNullOrEmpty();
        assertThat(registration.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(registration.getScopes())
                .containsExactly("openid", "profile", "offline_access", "api://opencrm-api/.default");
        var provider = registration.getProviderDetails();
        assertThat(provider.getAuthorizationUri()).isEqualTo(TENANT_BASE + "/oauth2/v2.0/authorize");
        assertThat(provider.getTokenUri()).isEqualTo(TENANT_BASE + "/oauth2/v2.0/token");
        assertThat(provider.getJwkSetUri()).isEqualTo(TENANT_BASE + "/discovery/v2.0/keys");
        assertThat(provider.getIssuerUri()).isEqualTo(TestTokens.ISSUER);
        assertThat(provider.getUserInfoEndpoint().getUri()).as("the API token must never go to Graph").isNullOrEmpty();
        assertThat(provider.getConfigurationMetadata())
                .containsEntry("end_session_endpoint", TENANT_BASE + "/oauth2/v2.0/logout");
    }

    @Test
    @DisplayName("a client secret (local tests) uses client_secret_post")
    void secretRegistration() {
        GatewayBffProperties.Login login = login();
        login.setClientSecret("local-secret");

        ClientRegistration registration = GatewayLoginConfiguration.registration(login, TestTokens.ISSUER,
                GatewayClientAuthentication.secret());

        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(registration.getClientSecret()).isEqualTo("local-secret");
    }

    @Test
    @DisplayName("multi-tenant, missing and v1.0 issuers are refused")
    void issuerChecks() {
        GatewayClientAuthentication secret = GatewayClientAuthentication.secret();
        assertThatThrownBy(() -> GatewayLoginConfiguration.registration(login(),
                "https://login.microsoftonline.com/common/v2.0", secret)).hasMessageContaining("tenant-specific");
        assertThatThrownBy(() -> GatewayLoginConfiguration.registration(login(), null, secret))
                .hasMessageContaining("issuer-uri");
        assertThatThrownBy(() -> GatewayLoginConfiguration.registration(login(),
                "https://sts.windows.net/00000000-0000-4000-8000-00000000c0de/", secret)).hasMessageContaining("v2.0");
    }

    @Test
    @DisplayName("credential: workload identity from AZURE_FEDERATED_TOKEN_FILE, secret only outside production")
    void credentialChoice() {
        MockEnvironment environment = new MockEnvironment();
        GatewayBffProperties.Login login = login();
        assertThatThrownBy(() -> GatewayLoginConfiguration.clientAuthentication(login, environment, false))
                .hasMessageContaining("needs a credential");

        environment.setProperty("AZURE_FEDERATED_TOKEN_FILE", "/var/run/secrets/azure/tokens/azure-identity-token");
        assertThat(GatewayLoginConfiguration.clientAuthentication(login, environment, true).usesFederatedAssertion())
                .isTrue();

        login.setClientSecret("s");
        environment.setProperty("opencrm.env", "prod");
        assertThatThrownBy(() -> GatewayLoginConfiguration.clientAuthentication(login, environment, true))
                .hasMessageContaining("client-secret is not allowed");
    }

    @Test
    @DisplayName("token refresh authenticates the gateway with the re-read workload identity assertion")
    void refreshSendsTheFederatedAssertion() throws IOException {
        Path file = Files.writeString(tempDir.resolve("azure-identity-token"), "projected-sa-token-1");
        AtomicReference<Map<String, String>> form = new AtomicReference<>();
        HttpServer tokenEndpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenEndpoint.createContext("/token", exchange -> {
            Map<String, String> fields = new LinkedHashMap<>();
            for (String pair : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                String[] kv = pair.split("=", 2);
                fields.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
            }
            form.set(fields);
            byte[] body = ("{\"access_token\":\"renewed\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                    + "\"refresh_token\":\"rt-2\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        tokenEndpoint.start();
        try {
            ClientRegistration registration = ClientRegistration.withRegistrationId("entra").clientId("gateway-client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://idp.test/authorize")
                    .tokenUri("http://127.0.0.1:" + tokenEndpoint.getAddress().getPort() + "/token")
                    .scope("openid", "offline_access", "api://opencrm-api/.default")
                    .build();
            OAuth2AccessToken expired = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "old",
                    Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));

            OAuth2AccessTokenResponse response = GatewayLoginConfiguration
                    .refreshClient(GatewayClientAuthentication.federated(file))
                    .getTokenResponse(new OAuth2RefreshTokenGrantRequest(registration, expired,
                            new OAuth2RefreshToken("rt-1", Instant.now())))
                    .block();

            assertThat(response.getAccessToken().getTokenValue()).isEqualTo("renewed");
            assertThat(form.get()).containsEntry("grant_type", "refresh_token")
                    .containsEntry("refresh_token", "rt-1")
                    .containsEntry("client_id", "gateway-client")
                    .containsEntry("client_assertion_type", GatewayClientAuthentication.JWT_BEARER_ASSERTION)
                    .containsEntry("client_assertion", "projected-sa-token-1")
                    .doesNotContainKey("client_secret");
        } finally {
            tokenEndpoint.stop(0);
        }
    }

    @Test
    @DisplayName("the session cookie is HttpOnly, Secure, SameSite=Lax (decision 2)")
    void sessionCookie() {
        GatewayBffProperties properties = new GatewayBffProperties();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));

        new GatewaySessionConfiguration().webSessionIdResolver(properties).setSessionId(exchange, "session-id");

        List<ResponseCookie> cookies = exchange.getResponse().getCookies().get("OPENCRM_SESSION");
        assertThat(cookies).hasSize(1);
        ResponseCookie cookie = cookies.getFirst();
        assertThat(cookie.getValue()).isEqualTo("session-id");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
