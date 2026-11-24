package com.opencrm.product.security;

import com.opencrm.security.web.EntraRoleAuthorities;
import com.opencrm.security.web.OpenCrmSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Catalog PUTs added for bundle, bundle-item, and configuration-rule edits are Admin-only.
 * Creates, deletes, and reads keep the baseline authenticated rule. A missing or unknown role is not Admin.
 */
@Configuration
public class CatalogPutAuthorization {

    static final String ADMIN = EntraRoleAuthorities.authorityFor("Admin");

    @Bean
    OpenCrmSecurityCustomizer catalogPutAdminOnly() {
        return rules -> rules.requestMatchers(HttpMethod.PUT,
                        "/api/v1/product-bundles/*/items/*",
                        "/api/v1/product-bundles/*",
                        "/api/v1/configuration/rules/*")
                .hasAuthority(ADMIN);
    }
}
