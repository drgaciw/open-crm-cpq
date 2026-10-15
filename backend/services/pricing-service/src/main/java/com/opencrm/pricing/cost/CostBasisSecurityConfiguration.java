package com.opencrm.pricing.cost;

import com.opencrm.security.web.EntraRoleAuthorities;
import com.opencrm.security.web.OpenCrmSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cost basis ingestion loads Finance data, so it needs the ADR-016 app role {@code Admin} ({@code ROLE_ADMIN}); no
 * role in the ADR-016 table is closer ({@code CFO} is an approval tier, not data administration). The rule is added
 * to the {@code libs/security} baseline chain in {@code jwt} mode; in {@code dev} mode the chain permits everything,
 * as for every other API.
 */
@Configuration(proxyBeanMethods = false)
class CostBasisSecurityConfiguration {

    static final String ROLE = "Admin";

    @Bean
    OpenCrmSecurityCustomizer costBasisAdminOnly() {
        String authority = EntraRoleAuthorities.authorityFor(ROLE);
        return rules -> rules.requestMatchers("/api/v1/cost-basis/**").hasAuthority(authority);
    }
}
