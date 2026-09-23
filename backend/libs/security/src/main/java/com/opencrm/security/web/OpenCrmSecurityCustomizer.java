package com.opencrm.security.web;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * How a service <em>adds</em> authorization rules to the baseline chain (ADR-016 decision 4: services may add rules
 * and may not replace the baseline). Rules are applied in {@code jwt} mode after the baseline's fixed rules
 * (public health, {@code /internal/**} needs the internal app role) and before its catch-alls, so a customizer can
 * tighten or open an API path but cannot open {@code /internal/**}. Declare it as a bean.
 */
@FunctionalInterface
public interface OpenCrmSecurityCustomizer {

    void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules);
}
