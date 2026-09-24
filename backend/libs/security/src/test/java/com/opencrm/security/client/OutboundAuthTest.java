package com.opencrm.security.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ADR-016 D5: OutboundAuth relays the user token or sends the service token")
class OutboundAuthTest {

    private static final ServiceTokenProvider SERVICE_TOKEN = () -> Optional.of("service-token");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "RS256").subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CPQ_USER")));
    }

    /** Sends one GET through {@code interceptor} and returns the Authorization header the server saw. */
    private static void expectAuthorization(ClientHttpRequestInterceptor interceptor, String expected) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://peer.test").requestInterceptor(interceptor);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://peer.test/api/v1/x")).andExpect(method(HttpMethod.GET))
                .andExpect(expected == null
                        ? headerDoesNotExist(HttpHeaders.AUTHORIZATION)
                        : header(HttpHeaders.AUTHORIZATION, expected))
                .andRespond(withSuccess());
        builder.build().get().uri("/api/v1/x").retrieve().toBodilessEntity();
        server.verify();
    }

    @Test
    @DisplayName("relay: the caller's bearer token goes downstream")
    void relaysUserToken() {
        signIn("user-token");

        expectAuthorization(new OutboundAuth(SERVICE_TOKEN, false).relayUserToken(), "Bearer user-token");
    }

    @Test
    @DisplayName("relay without a user (consumer, scheduler, other thread): the service token")
    void relayFallsBackToServiceToken() {
        expectAuthorization(new OutboundAuth(SERVICE_TOKEN, false).relayUserToken(), "Bearer service-token");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        expectAuthorization(new OutboundAuth(SERVICE_TOKEN, false).relayUserToken(), "Bearer service-token");
    }

    @Test
    @DisplayName("serviceToken: always the service token, even inside a user request")
    void serviceTokenIgnoresTheUser() {
        signIn("user-token");

        expectAuthorization(new OutboundAuth(SERVICE_TOKEN, false).serviceToken(), "Bearer service-token");
    }

    @Test
    @DisplayName("an Authorization header set by the caller is left alone")
    void explicitHeaderWins() {
        signIn("user-token");
        OutboundAuth auth = new OutboundAuth(SERVICE_TOKEN, false);
        for (Function<OutboundAuth, ClientHttpRequestInterceptor> kind :
                List.<Function<OutboundAuth, ClientHttpRequestInterceptor>>of(
                        OutboundAuth::relayUserToken, OutboundAuth::serviceToken)) {
            RestClient.Builder builder = RestClient.builder().requestInterceptor(kind.apply(auth));
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://peer.test/x")).andExpect(header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                    .andRespond(withSuccess());
            builder.build().get().uri("http://peer.test/x").header(HttpHeaders.AUTHORIZATION, "Basic abc")
                    .retrieve().toBodilessEntity();
            server.verify();
        }
    }

    @Test
    @DisplayName("no user and no service identity: sent without a token (dev mode)")
    void noTokenOutsideProduction() {
        OutboundAuth auth = OutboundAuth.withoutServiceIdentity();

        expectAuthorization(auth.relayUserToken(), null);
        expectAuthorization(auth.serviceToken(), null);
    }

    @Test
    @DisplayName("no token with the production marker in jwt mode: ServiceTokenException, nothing sent")
    void noTokenInProductionFails() {
        OutboundAuth auth = new OutboundAuth(ServiceTokenProvider.none(), true);
        RestClient client = RestClient.builder().requestInterceptor(auth.serviceToken()).build();

        assertThatThrownBy(() -> client.get().uri("http://peer.test/internal/v1/x").retrieve().toBodilessEntity())
                .isInstanceOf(ServiceTokenException.class)
                .hasMessageContaining("/internal/v1/x")
                .hasMessageContaining("AZURE_CLIENT_ID");
    }

    @Test
    @DisplayName("currentUserToken reads only authenticated bearer tokens")
    void currentUserToken() {
        assertThat(OutboundAuth.currentUserToken()).isEmpty();
        signIn("abc");
        assertThat(OutboundAuth.currentUserToken()).contains("abc");
    }
}
