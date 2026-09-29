package com.opencrm.security.web;

import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Locale;
import java.util.Set;

/**
 * The one {@link SecurityFilterChain} of every servlet service (ADR-016 decisions 4 and 9). Services do not declare
 * their own chain; they add rules with an {@link OpenCrmSecurityCustomizer} bean. {@code EventContractFitnessTest}'s
 * sibling, {@code SecurityBaselineFitnessTest}, keeps it that way.
 *
 * <p><strong>{@code jwt} mode</strong> (every profile except {@code dev}): an OAuth 2.0 resource server.
 * <ul>
 *   <li>{@code /actuator/health/**} and {@code /actuator/info} are public;</li>
 *   <li>{@code /internal/**} needs a service token with the internal app role ({@code Internal.Invoke}); callers
 *       attach it with {@code OutboundAuth.serviceToken()};</li>
 *   <li>{@code /api/v1/**} and everything else need an authenticated token;</li>
 *   <li>tokens are validated by {@link OpenCrmJwtDecoders}; app roles map through {@link EntraRoleAuthorities}.</li>
 * </ul>
 * Stateless, CSRF off: services receive bearer tokens, never cookies (ADR-004 keeps CSRF at the edge).
 *
 * <p><strong>Management port</strong> (owner decision 4): with {@code management.server.port} set to a port other than
 * the service's, the actuator runs on that second port and a second chain, ahead of the one above, governs it: only
 * {@code health}, {@code info} and {@code prometheus} are served, without a token, and everything else is refused.
 * Metrics are scraped without a credential, so that port must be reachable from the monitoring namespace only
 * (a Kubernetes NetworkPolicy; see {@code docs/runbooks/identity-go-live.md}), and never through an ingress or the
 * gateway. Without that setting the actuator shares the service port and the chain above applies to it, so
 * {@code prometheus} needs a token there.
 * * <p><strong>{@code dev} mode</strong>: permits everything, logs a WARN, and refuses to start when the production
 * marker {@code opencrm.env} ({@code OPENCRM_ENV}) is {@code prod} or {@code production}.
 */
@Slf4j
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, JwtDecoder.class})
@EnableConfigurationProperties(OpenCrmSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class OpenCrmSecurityAutoConfiguration {

    /** Values of {@code opencrm.env} that mark a production deployment. */
    static final Set<String> PRODUCTION_MARKERS = Set.of("prod", "production");

    @Bean
    public SecurityFilterChain openCrmSecurityFilterChain(HttpSecurity http, OpenCrmSecurityProperties properties,
                                                         Environment environment, JwtDecoder jwtDecoder,
                                                         ObjectProvider<OpenCrmSecurityCustomizer> customizers)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (mode(properties, environment) == SecurityMode.DEV) {
            log.warn("opencrm.security.mode=dev: every request is permitted without authentication. "
                    + "Local development only (ADR-016).");
            return http.authorizeHttpRequests(rules -> rules.anyRequest().permitAll()).build();
        }
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(
                new EntraRoleAuthorities(properties.getJwt().getRolesClaim()));
        String internalAuthority = EntraRoleAuthorities.authorityFor(properties.getInternalRole());
        return http
                .authorizeHttpRequests(rules -> {
                    rules.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    if (properties.isPermitApiDocs()) {
                        rules.requestMatchers("/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll();
                    }
                    rules.requestMatchers("/internal/**").hasAuthority(internalAuthority);
                    customizers.orderedStream().forEach(customizer -> customizer.customize(rules));
                    rules.requestMatchers("/api/v1/**").authenticated();
                    rules.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(authentication)))
                .build();
    }

    /**
     * The chain of the management port (owner decision 4). It exists only when {@code management.server.port} differs
     * from the service port, and it matches only requests that arrive on that port. It is ordered ahead of
     * {@code openCrmSecurityFilterChain}, which keeps the service port.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(EndpointRequest.class)
    @ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
    static class ManagementPortConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain openCrmManagementFilterChain(HttpSecurity http, OpenCrmSecurityProperties properties,
                                                        Environment environment) throws Exception {
            http.securityMatcher(ManagementPortConfiguration::isManagementRequest)
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            if (mode(properties, environment) == SecurityMode.DEV) {
                return http.authorizeHttpRequests(rules -> rules.anyRequest().permitAll()).build();
            }
            return http.authorizeHttpRequests(rules -> rules
                            .requestMatchers(EndpointRequest.to(ManagementEndpoints.publicIds())).permitAll()
                            .anyRequest().denyAll())
                    .build();
        }

        /** Whether the request arrived at the management web server rather than at the service's. */
        static boolean isManagementRequest(HttpServletRequest request) {
            WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
            return context != null && WebServerApplicationContext.hasServerNamespace(context, "management");
        }
    }
    /** The token decoder of {@code jwt} mode. In {@code dev} mode it exists but is never used. */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder openCrmJwtDecoder(OpenCrmSecurityProperties properties, Environment environment) {
        if (mode(properties, environment) == SecurityMode.DEV) {
            return token -> {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("opencrm.security.mode=dev");
            };
        }
        return OpenCrmJwtDecoders.create(properties.getJwt(), isProduction(environment));
    }

    /**
     * {@code opencrm.security.mode}, else {@code dev} under the {@code dev} profile and {@code jwt} otherwise.
     *
     * @throws IllegalStateException for {@code dev} mode when the production marker is set
     */
    public static SecurityMode mode(OpenCrmSecurityProperties properties, Environment environment) {
        SecurityMode mode = properties.getMode() != null
                ? properties.getMode()
                : environment.acceptsProfiles(Profiles.of("dev")) ? SecurityMode.DEV : SecurityMode.JWT;
        if (mode == SecurityMode.DEV && isProduction(environment)) {
            throw new IllegalStateException("opencrm.security.mode=dev is not allowed when opencrm.env="
                    + environment.getProperty("opencrm.env") + " (ADR-016 decision 4)");
        }
        return mode;
    }

    /** Whether the production marker {@code opencrm.env} ({@code OPENCRM_ENV}) is {@code prod} or {@code production}. */
    public static boolean isProduction(Environment environment) {
        String marker = environment.getProperty("opencrm.env");
        return marker != null && PRODUCTION_MARKERS.contains(marker.trim().toLowerCase(Locale.ROOT));
    }
}
