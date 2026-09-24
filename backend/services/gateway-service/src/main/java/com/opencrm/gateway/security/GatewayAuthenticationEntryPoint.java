package com.opencrm.gateway.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * What an unauthenticated request gets from the gateway (ADR-016 step 3).
 * <ul>
 *   <li>A browser navigating to a page (GET, HTML, not {@code /api/**}) is redirected to the login.</li>
 *   <li>Everything else, the SPA's {@code /api} calls and agents included, gets {@code 401} with a Bearer challenge.
 *       When a login exists, the {@value #LOGIN_HINT_HEADER} header names it, and the SPA sends the browser there. A
 *       redirect would be wrong for XHR: the browser would follow it into Entra's HTML inside the API call.</li>
 * </ul>
 */
public final class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    /** Response header naming the login path, read by the SPA's auth interceptor. Relative paths only. */
    public static final String LOGIN_HINT_HEADER = "X-OpenCRM-Login";

    private static final byte[] BODY = "{\"success\":false,\"message\":\"Authentication required\"}"
            .getBytes(StandardCharsets.UTF_8);

    private final String loginPath;
    private final ServerAuthenticationEntryPoint redirect;

    /** @param loginPath the login path, or null when the gateway has no browser login (bearer lane only) */
    public GatewayAuthenticationEntryPoint(String loginPath) {
        this.loginPath = loginPath;
        this.redirect = loginPath == null ? null : new RedirectServerAuthenticationEntryPoint(loginPath);
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        if (redirect != null && isPageNavigation(exchange.getRequest())) {
            return redirect.commence(exchange, ex);
        }
        return unauthorized(exchange.getResponse(), loginPath);
    }

    /** 401, a Bearer challenge, the login hint when there is a login, and a JSON body in the ApiResponse shape. */
    static Mono<Void> unauthorized(ServerHttpResponse response, String loginPath) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = response.getHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (loginPath != null) {
            headers.set(LOGIN_HINT_HEADER, loginPath);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(BODY);
        return response.writeWith(Mono.just(body));
    }

    static boolean isPageNavigation(ServerHttpRequest request) {
        if (!HttpMethod.GET.equals(request.getMethod()) || request.getPath().value().startsWith("/api/")) {
            return false;
        }
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeaders().getFirst("X-Requested-With"))) {
            return false;
        }
        List<MediaType> accept = request.getHeaders().getAccept();
        return accept.stream().anyMatch(type -> type.isCompatibleWith(MediaType.TEXT_HTML) && !type.isWildcardType());
    }
}
