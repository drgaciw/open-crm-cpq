package com.opencrm.gateway.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * The browser's only credential (ADR-016 decision 2): an opaque session id in an {@code HttpOnly; Secure; SameSite=Lax}
 * cookie. The session behind it, with the user's tokens, is stored in Redis by Spring Session, so any gateway replica
 * serves any request. The {@code XSRF-TOKEN} cookie of ADR-004 is separate and readable by the SPA on purpose.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayBffProperties.class)
public class GatewaySessionConfiguration {

    @Bean
    public WebSessionIdResolver webSessionIdResolver(GatewayBffProperties properties) {
        GatewayBffProperties.Session session = properties.getSession();
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName(session.getCookieName());
        resolver.addCookieInitializer(cookie -> cookie
                .httpOnly(true)
                .secure(session.isCookieSecure())
                .sameSite("Lax")
                .path("/"));
        return resolver;
    }

    /** Strips inbound identity headers and relays the one token the gateway vouches for. */
    @Bean
    public IdentityRelayFilter identityRelayFilter(ObjectProvider<GatewayLogin> login) {
        GatewayLogin configured = login.getIfAvailable();
        return configured == null
                ? new IdentityRelayFilter(null, null)
                : new IdentityRelayFilter(configured.authorizedClientManager(), configured.loginPath());
    }
}
