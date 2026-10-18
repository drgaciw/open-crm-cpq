package com.opencrm.pricing.renewal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Rejects a renewal resolve body larger than {@link RenewalResolveLimits#MAX_BODY_BYTES}.
 * This is not a request-rate limiter. The abuse and latency gaps are recorded in the security criteria.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RenewalResolveBodyLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isResolve(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(RenewalResolveLimits.MAX_BODY_BYTES + 1);
        if (body.length > RenewalResolveLimits.MAX_BODY_BYTES) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"errorCode\":\"REQUEST_TOO_LARGE\",\"message\":\"Request body exceeds "
                            + RenewalResolveLimits.MAX_BODY_BYTES + " bytes\"}");
            return;
        }
        filterChain.doFilter(new BoundedBodyRequest(request, body), response);
    }

    private static boolean isResolve(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return RenewalResolveController.PATH.equals(path);
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BoundedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // The body is already buffered.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
