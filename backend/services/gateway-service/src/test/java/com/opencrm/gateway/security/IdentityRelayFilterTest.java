package com.opencrm.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("ADR-016 D3: the gateway strips inbound identity and relays one token")
class IdentityRelayFilterTest {

    private static MockServerHttpRequest.BaseBuilder<?> spoofedRequest() {
        return MockServerHttpRequest.get("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Basic YWxpY2U6cHc=")
                .header("X-User-Id", "ceo")
                .header("X-Actor", "cfo")
                .header("X-Auth-Request-User", "admin")
                .header("X-Forwarded-User", "admin")
                .header("X-MS-CLIENT-PRINCIPAL-NAME", "admin")
                .header("X-Correlation-Id", "keep-me");
    }

    /** Runs the filter and returns the request the route would forward, or null when the filter answered itself. */
    private static HttpHeaders forwarded(IdentityRelayFilter filter, ServerWebExchange exchange) {
        AtomicReference<HttpHeaders> seen = new AtomicReference<>();
        GatewayFilterChain chain = forwardedExchange -> {
            seen.set(forwardedExchange.getRequest().getHeaders());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    private static ServerWebExchange as(Principal principal) {
        MockServerWebExchange exchange = MockServerWebExchange.from(spoofedRequest());
        return principal == null ? exchange : exchange.mutate().principal(Mono.just(principal)).build();
    }

    private static void assertNoInboundIdentity(HttpHeaders headers) {
        assertThat(headers.headerNames()).noneMatch(name -> name.equalsIgnoreCase("X-User-Id")
                || name.equalsIgnoreCase("X-Actor") || name.equalsIgnoreCase("X-Auth-Request-User")
                || name.equalsIgnoreCase("X-Forwarded-User") || name.equalsIgnoreCase("X-MS-CLIENT-PRINCIPAL-NAME"));
        assertThat(headers.getFirst("X-Correlation-Id")).isEqualTo("keep-me");
    }

    @Test
    @DisplayName("no authentication (dev mode): every inbound identity header and Authorization is removed")
    void anonymousStripsEverything() {
        HttpHeaders headers = forwarded(new IdentityRelayFilter(null, null), as(null));

        assertNoInboundIdentity(headers);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    @DisplayName("bearer lane: the validated token is forwarded, nothing else")
    void bearerLaneRelaysTheValidatedToken() {
        Jwt jwt = Jwt.withTokenValue("validated-agent-token").header("alg", "RS256").subject("agent")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();

        HttpHeaders headers = forwarded(new IdentityRelayFilter(null, null),
                as(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES)));

        assertNoInboundIdentity(headers);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer validated-agent-token");
    }

    @Test
    @DisplayName("browser session: the user's access token from the session is relayed (TokenRelay)")
    void sessionRelaysTheUsersAccessToken() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("entra").clientId("gateway-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://idp.test/authorize").tokenUri("https://idp.test/token").build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "users-api-token",
                Instant.now(), Instant.now().plusSeconds(600));
        AtomicReference<String> askedFor = new AtomicReference<>();
        ReactiveOAuth2AuthorizedClientManager clients = request -> {
            askedFor.set(request.getClientRegistrationId() + ":" + request.getPrincipal().getName());
            return Mono.just(new OAuth2AuthorizedClient(registration, request.getPrincipal().getName(), accessToken));
        };
        OAuth2AuthenticationToken session = new OAuth2AuthenticationToken(
                new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("sub", "alice"), "sub"),
                AuthorityUtils.NO_AUTHORITIES, "entra");

        HttpHeaders headers = forwarded(new IdentityRelayFilter(clients, "/oauth2/authorization/entra"), as(session));

        assertNoInboundIdentity(headers);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer users-api-token");
        assertThat(askedFor.get()).isEqualTo("entra:alice");
    }

    @Test
    @DisplayName("a session whose token cannot be renewed gets 401 and the login hint; nothing is forwarded")
    void unrenewableSessionAsksForLogin() {
        ReactiveOAuth2AuthorizedClientManager clients =
                request -> Mono.error(new ClientAuthorizationRequiredException("entra"));
        OAuth2AuthenticationToken session = new OAuth2AuthenticationToken(
                new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("sub", "alice"), "sub"),
                AuthorityUtils.NO_AUTHORITIES, "entra");
        ServerWebExchange exchange = as(session);

        HttpHeaders headers = forwarded(new IdentityRelayFilter(clients, "/oauth2/authorization/entra"), exchange);

        assertThat(headers).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayAuthenticationEntryPoint.LOGIN_HINT_HEADER))
                .isEqualTo("/oauth2/authorization/entra");
    }

    @Test
    @DisplayName("an OAuth2 error raised further down the chain is not turned into a login request")
    void downstreamErrorsAreNotLoginRequests() {
        ServerWebExchange exchange = as(null);
        GatewayFilterChain failing = forwardedExchange ->
                Mono.error(new ClientAuthorizationRequiredException("some-other-client"));

        assertThatThrownBy(() -> new IdentityRelayFilter(null, "/oauth2/authorization/entra")
                .filter(exchange, failing).block())
                .isInstanceOf(ClientAuthorizationRequiredException.class);
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayAuthenticationEntryPoint.LOGIN_HINT_HEADER))
                .isNull();
    }

    @Test
    @DisplayName("runs early: before any route filter")
    void order() {
        assertThat(new IdentityRelayFilter(null, null).getOrder()).isLessThan(0);
    }
}
