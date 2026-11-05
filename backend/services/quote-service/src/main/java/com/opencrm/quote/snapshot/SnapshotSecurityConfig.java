package com.opencrm.quote.snapshot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Keeps the OMS issuer guard in addition to the shared ADR016 chain and controller scope checks. */
@Configuration
public class SnapshotSecurityConfig implements WebMvcConfigurer {
    private final String issuer;

    public SnapshotSecurityConfig(@Value("${oms.quote.snapshot-issuer-uri:}") String issuer) {
        this.issuer = issuer;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (issuer.isBlank() || authentication == null || !authentication.isAuthenticated()
                        || !(authentication.getPrincipal() instanceof Jwt jwt)
                        || jwt.getIssuer() == null || !issuer.equals(jwt.getIssuer().toString())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/internal/oms/**");
    }
}
