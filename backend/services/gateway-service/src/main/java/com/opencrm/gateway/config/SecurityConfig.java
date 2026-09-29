package com.opencrm.gateway.config;

import com.opencrm.gateway.security.GatewayAuthenticationEntryPoint;
import com.opencrm.gateway.security.GatewayBffProperties;
import com.opencrm.gateway.security.GatewayLogin;
import com.opencrm.security.web.ManagementEndpoints;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.reactive.EndpointRequest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * The gateway's security chain (ADR-016 decisions 2 and 3; ADR-004 for CSRF).
 *
 * <p><strong>{@code dev} mode</strong> ({@code opencrm.security.mode}, which defaults to {@code dev} under the
 * {@code dev} profile): every request is permitted, CSRF stays on.
 *
 * <p><strong>{@code jwt} mode</strong>: the gateway is a backend-for-frontend with two ways in.
 * <ul>
 *   <li><strong>Browser session.</strong> {@code /oauth2/authorization/entra} starts the Entra login (authorization
 *       code + PKCE). The tokens stay in the session in Redis; the browser holds an {@code HttpOnly} session cookie.
 *       Cookie-authenticated state changes need the ADR-004 CSRF token. {@code POST /logout} ends the session and
 *       signs out at Entra.</li>
 *   <li><strong>Bearer lane.</strong> Agents and ai-service send {@code Authorization: Bearer}. The gateway validates it
 *       like every service does. Such requests carry no ambient credential, so CSRF does not apply to them.</li>
 * </ul>
 * Health and info are public; everything else is authenticated. An unauthenticated API call gets 401 with the login
 * hint ({@link GatewayAuthenticationEntryPoint}); a page navigation is redirected to the login. {@code IdentityRelayFilter}
 * then forwards the one token the gateway vouches for. Without {@code opencrm.gateway.login.client-id} there is no
 * browser login, and the production marker refuses to start that way.
 *
 * <p><strong>Management port</strong> (owner decision 4): with {@code management.server.port} set to a port other than
 * the gateway's, a second chain governs that port. It serves {@code health}, {@code info} and {@code prometheus}
 * without a token and refuses everything else, the {@code gateway} route inspection included. That port must be
 * reachable from the monitoring namespace only, and is never routed or published through the ingress.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties({OpenCrmSecurityProperties.class, GatewayBffProperties.class})
public class SecurityConfig {

    private static final Set<HttpMethod> SAFE_METHODS =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.OPTIONS);

    @Bean
    public SecurityWebFilterChain gatewaySecurityFilterChain(ServerHttpSecurity http, OpenCrmSecurityProperties security,
                                                             Environment environment, ReactiveJwtDecoder jwtDecoder,
                                                             ObjectProvider<GatewayLogin> loginProvider) {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(cookieAuthenticatedStateChange()))
                .addFilterAfter(issueCsrfCookie(), SecurityWebFiltersOrder.CSRF);
        if (OpenCrmSecurityAutoConfiguration.mode(security, environment) == SecurityMode.DEV) {
            log.warn("opencrm.security.mode=dev: the gateway permits every request without authentication. "
                    + "Local development only (ADR-016).");
            return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
        }

        GatewayLogin login = loginProvider.getIfAvailable();
        if (login == null) {
            if (OpenCrmSecurityAutoConfiguration.isProduction(environment)) {
                throw new IllegalStateException("opencrm.gateway.login.client-id is required when opencrm.env="
                        + environment.getProperty("opencrm.env") + ": the BFF login is the only way in for browsers "
                        + "(ADR-016 decision 2)");
            }
            log.warn("No browser login (opencrm.gateway.login.client-id is unset): the gateway accepts bearer tokens "
                    + "only.");
        }
        http.authorizeExchange(exchanges -> {
                    exchanges.pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    if (security.isPermitApiDocs()) {
                        exchanges.pathMatchers("/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/webjars/**").permitAll();
                    }
                    exchanges.anyExchange().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new GatewayAuthenticationEntryPoint(login == null ? null : login.loginPath())))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)));
        if (login != null) {
            http.oauth2Login(oauth2 -> oauth2
                            .clientRegistrationRepository(login.registrations())
                            .authorizedClientRepository(login.authorizedClients())
                            .authenticationManager(login.authenticationManager())
                            .authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler(login.successUrl())))
                    .logout(logout -> logout
                            .logoutHandler(new DelegatingServerLogoutHandler(new SecurityContextServerLogoutHandler(),
                                    new WebSessionServerLogoutHandler()))
                            .logoutSuccessHandler(login.logoutSuccessHandler()));
        }
        return http.build();
    }

    /**
     * The chain of the management port. It exists only when {@code management.server.port} differs from the gateway's
     * port, and matches only requests that arrive on that port; it is ordered ahead of the chain above.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
    static class ManagementPortConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityWebFilterChain gatewayManagementFilterChain(ServerHttpSecurity http, OpenCrmSecurityProperties security,
                                                            Environment environment) {
            // Nothing here authenticates, so a refusal is a plain 403 for everyone: no Basic challenge is advertised.
            http.securityMatcher(ManagementPortConfiguration::onManagementPort)
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.FORBIDDEN)));
            if (OpenCrmSecurityAutoConfiguration.mode(security, environment) == SecurityMode.DEV) {
                return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
            }
            return http.authorizeExchange(exchanges -> exchanges
                            .matchers(EndpointRequest.to(ManagementEndpoints.publicIds())).permitAll()
                            .anyExchange().denyAll())
                    .build();
        }

        /** Whether the request arrived at the management web server rather than at the gateway's. */
        static Mono<ServerWebExchangeMatcher.MatchResult> onManagementPort(ServerWebExchange exchange) {
            ApplicationContext context = exchange.getApplicationContext();
            return context != null && WebServerApplicationContext.hasServerNamespace(context, "management")
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();
        }
    }
    /**
     * WebFlux defers the CSRF token: the {@code XSRF-TOKEN} cookie is only written when something subscribes to the
     * token. Without this filter the SPA never receives the cookie, so it cannot send {@code X-XSRF-TOKEN}, and every
     * cookie-authenticated state change, {@code POST /logout} included, is refused (ADR-016 step 5).
     */
    static WebFilter issueCsrfCookie() {
        return (exchange, chain) -> {
            Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
            return (token != null ? token : Mono.<CsrfToken>empty()).then(chain.filter(exchange));
        };
    }

    /**
     * CSRF (ADR-004) applies to state-changing requests that a browser could send on its own: everything except a
     * request carrying {@code Authorization: Bearer}. Browsers never attach that header automatically, so a bearer
     * request cannot be forged cross-site; its token is validated by the resource server instead.
     */
    static ServerWebExchangeMatcher cookieAuthenticatedStateChange() {
        return exchange -> {
            if (SAFE_METHODS.contains(exchange.getRequest().getMethod())) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            boolean bearer = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
            return bearer ? ServerWebExchangeMatcher.MatchResult.notMatch() : ServerWebExchangeMatcher.MatchResult.match();
        };
    }
}
