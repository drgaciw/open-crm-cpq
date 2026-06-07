package com.opencrm.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight CSRF protection for cookie-authenticated browser requests.
 * For state-changing requests that include cookies, enforce allowed Origin/Referer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfProtectionFilter implements WebFilter {

    private static final Set<HttpMethod> STATE_CHANGING_METHODS = Set.of(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE
    );

    private final List<String> allowedOrigins;

    public CsrfProtectionFilter(
            @Value("${security.csrf.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (!STATE_CHANGING_METHODS.contains(request.getMethod())) {
            return chain.filter(exchange);
        }

        // CSRF risk applies when credentials are automatically sent by browser cookies.
        if (request.getHeaders().getFirst(HttpHeaders.COOKIE) == null) {
            return chain.filter(exchange);
        }

        String originHeader = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (matchesAllowedOrigin(originHeader)) {
            return chain.filter(exchange);
        }

        String refererHeader = request.getHeaders().getFirst(HttpHeaders.REFERER);
        if (matchesAllowedReferer(refererHeader)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private boolean matchesAllowedReferer(String refererHeader) {
        if (!StringUtils.hasText(refererHeader)) {
            return false;
        }

        try {
            URI referer = new URI(refererHeader);
            if (!StringUtils.hasText(referer.getScheme()) || !StringUtils.hasText(referer.getHost())) {
                return false;
            }
            String originFromReferer = referer.getScheme().toLowerCase(Locale.ROOT)
                    + "://" + referer.getHost().toLowerCase(Locale.ROOT)
                    + buildPortSuffix(referer);
            return matchesAllowedOrigin(originFromReferer);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private boolean matchesAllowedOrigin(String originHeader) {
        if (!StringUtils.hasText(originHeader)) {
            return false;
        }

        URI originUri = parseUri(originHeader);
        if (originUri == null || !StringUtils.hasText(originUri.getScheme()) || !StringUtils.hasText(originUri.getHost())) {
            return false;
        }

        String normalizedScheme = originUri.getScheme().toLowerCase(Locale.ROOT);
        String normalizedHost = originUri.getHost().toLowerCase(Locale.ROOT);
        int normalizedPort = resolvePort(originUri);

        for (String allowedOrigin : allowedOrigins) {
            if (!StringUtils.hasText(allowedOrigin)) continue;
            if (matchesPattern(normalizedScheme, normalizedHost, normalizedPort, allowedOrigin.trim())) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesPattern(String requestScheme, String requestHost, int requestPort, String allowedPattern) {
        URI allowedUri = parseUri(allowedPattern.replace("*.", "wildcard."));
        if (allowedUri == null || !StringUtils.hasText(allowedUri.getScheme()) || !StringUtils.hasText(allowedUri.getHost())) {
            return false;
        }

        String allowedScheme = allowedUri.getScheme().toLowerCase(Locale.ROOT);
        String allowedHost = allowedUri.getHost().toLowerCase(Locale.ROOT);
        int allowedPort = resolvePort(allowedUri);

        if (!requestScheme.equals(allowedScheme)) {
            return false;
        }

        boolean hostMatches;
        if (allowedPattern.contains("*.")) {
            String suffix = allowedHost.replaceFirst("^wildcard\\.", ".");
            hostMatches = requestHost.endsWith(suffix) && requestHost.length() > suffix.length();
        } else {
            hostMatches = requestHost.equals(allowedHost);
        }

        if (!hostMatches) {
            return false;
        }

        return allowedPort == requestPort;
    }

    private URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private int resolvePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String buildPortSuffix(URI uri) {
        int port = uri.getPort();
        if (port <= 0) {
            return "";
        }
        return ":" + port;
    }
}
