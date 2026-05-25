package com.opencrm.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Gateway rate-limiter key resolution strategy.
 */
@Configuration
public class RateLimitingConfig {

    private final XForwardedRemoteAddressResolver remoteAddressResolver = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    /**
     * Resolve a stable key per caller (authenticated user when available, else client IP).
     */
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> principal.getName())
                .filter(StringUtils::hasText)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + resolveClientIp(exchange)));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = remoteAddressResolver.resolve(exchange);
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "anonymous";
    }
}
