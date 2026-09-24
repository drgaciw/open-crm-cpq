package com.opencrm.gateway.config;

import com.opencrm.gateway.security.GatewayAuthenticationEntryPoint;
import com.opencrm.gateway.security.GatewayBffProperties;
import com.opencrm.gateway.security.GatewayLogin;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;

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
                .requireCsrfProtectionMatcher(cookieAuthenticatedStateChange()));
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
