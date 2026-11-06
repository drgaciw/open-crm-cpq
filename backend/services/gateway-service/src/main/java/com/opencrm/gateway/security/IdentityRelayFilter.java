package com.opencrm.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the gateway forwards as identity (ADR-016 decision 3). It runs on every routed request, before the route's own
 * filters and the proxy call:
 * <ol>
 *   <li><strong>Strip.</strong> Every inbound {@code Authorization} header and every identity-looking header
 *       ({@code X-User-*}, {@code X-Actor*}, {@code X-Auth-*}, {@code X-Forwarded-User}, {@code X-Remote-User},
 *       {@code X-MS-Client-Principal*}) is removed. A service never sees an identity the gateway did not establish.</li>
 *   <li><strong>Relay.</strong> One bearer token is added:
 *     <ul>
 *       <li>a browser session (BFF login): the user's access token from the session, refreshed when it has expired.
 *           This is Spring Cloud Gateway's {@code TokenRelay}, folded in here so stripping and relaying happen in one
 *           ordered step;</li>
 *       <li>the bearer lane (agents, ai-service): the token the gateway has just validated;</li>
 *       <li>no authentication ({@code dev} mode): nothing.</li>
 *     </ul>
 *   </li>
 * </ol>
 * If the session's token can no longer be refreshed, the request ends with {@code 401} and the login hint, so the SPA
 * sends the browser through the login again.
 */
@Slf4j
public class IdentityRelayFilter implements GlobalFilter, Ordered {

    /** Early, so no later filter sees an inbound identity header. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private static final List<String> STRIPPED_PREFIXES = List.of(
            "x-user-", "x-actor", "x-auth-", "x-forwarded-user", "x-remote-user", "x-ms-client-principal");

    /** The OMS browser-login registration (application-oms-browser.yml); see {@link #accessToken}. */
    private static final String OMS_REGISTRATION_ID = "oms";

    private final ReactiveOAuth2AuthorizedClientManager authorizedClients;
    private final String loginPath;

    /**
     * @param authorizedClients the login's token store and refresher, or null without a browser login
     * @param loginPath         the login path for the 401 hint, or null without a browser login
     */
    public IdentityRelayFilter(ReactiveOAuth2AuthorizedClientManager authorizedClients, String loginPath) {
        this.authorizedClients = authorizedClients;
        this.loginPath = loginPath;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Only a failure to obtain the token asks for a new login; errors further down the chain are not ours.
        return exchange.getPrincipal()
                .flatMap(principal -> accessToken(principal, exchange))
                .map(Relay::forward)
                .defaultIfEmpty(Relay.ANONYMOUS)
                .onErrorResume(OAuth2AuthorizationException.class, e -> {
                    log.info("Session token for {} could not be renewed ({}); asking for a new login",
                            exchange.getRequest().getPath(), e.getError().getErrorCode());
                    return Mono.just(Relay.LOGIN_REQUIRED);
                })
                .flatMap(relay -> relay.loginRequired()
                        ? GatewayAuthenticationEntryPoint.unauthorized(exchange.getResponse(), loginPath)
                        : chain.filter(exchange.mutate()
                                .request(request -> request.headers(headers -> rewrite(headers, relay.token())))
                                .build()));
    }

    /** The outcome of looking up the caller's token: forward it (or nothing), or send the caller to the login. */
    private record Relay(Optional<String> token, boolean loginRequired) {
        static final Relay ANONYMOUS = new Relay(Optional.empty(), false);
        static final Relay LOGIN_REQUIRED = new Relay(Optional.empty(), true);

        static Relay forward(String token) {
            return new Relay(Optional.of(token), false);
        }
    }

    private Mono<String> accessToken(Principal principal, ServerWebExchange exchange) {
        if (principal instanceof JwtAuthenticationToken bearer) {
            return Mono.just(bearer.getToken().getTokenValue());
        }
        // The OMS session belongs to the OMS registration, which this login's token store does not hold. Its token is
        // relayed by OrderTokenRelay instead, so this filter leaves it alone rather than failing before that relay runs.
        if (principal instanceof OAuth2AuthenticationToken session && authorizedClients != null
                && !OMS_REGISTRATION_ID.equals(session.getAuthorizedClientRegistrationId())) {
            String registrationId = session.getAuthorizedClientRegistrationId();
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                    .principal(session)
                    .attribute(ServerWebExchange.class.getName(), exchange)
                    .build();
            return authorizedClients.authorize(request)
                    .switchIfEmpty(Mono.error(() -> new ClientAuthorizationRequiredException(registrationId)))
                    .map(client -> client.getAccessToken().getTokenValue());
        }
        return Mono.empty();
    }

    /** Removes inbound identity headers, then sets the relayed token, if any. */
    static void rewrite(HttpHeaders headers, Optional<String> token) {
        List<String> names = List.copyOf(headers.headerNames());
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("authorization") || STRIPPED_PREFIXES.stream().anyMatch(lower::startsWith)) {
                headers.remove(name);
            }
        }
        token.ifPresent(headers::setBearerAuth);
    }
}
