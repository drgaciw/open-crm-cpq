package com.opencrm.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Gateway rate-limiter key resolution strategy.
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Resolve a stable key per caller (authenticated user when available, else client IP).
     */
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> principal.getName())
                .filter(StringUtils::hasText)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + resolveClientIp(exchange.getRequest().getHeaders(),
                        exchange.getRequest().getRemoteAddress())));
    }

    private String resolveClientIp(HttpHeaders headers, InetSocketAddress remoteAddress) {
        String forwardedFor = headers.getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String first = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }

        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "anonymous";
    }
}
