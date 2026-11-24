package com.opencrm.pricebook.security;

import com.opencrm.security.web.EntraRoleAuthorities;
import com.opencrm.security.web.OpenCrmSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * {@code PUT /api/v1/price-books/{id}} is Admin-only, including a rename that sets no clear flag.
 * Entry updates stay on the baseline authenticated rule: the pattern is one path segment.
 */
@Configuration
public class PriceBookPutAuthorization {

    static final String ADMIN = EntraRoleAuthorities.authorityFor("Admin");

    @Bean
    OpenCrmSecurityCustomizer priceBookPutAdminOnly() {
        return rules -> rules.requestMatchers(HttpMethod.PUT, "/api/v1/price-books/*")
                .hasAuthority(ADMIN);
    }
}
