package com.opencrm.quote.security;

import com.opencrm.quote.lifecycle.LifecycleAuthority;
import com.opencrm.security.web.OpenCrmSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Who may bulk-remove quote lines.
 *
 * <p>Line add, update, and single delete have no role matcher. The closed set that already guards a commercial
 * quote write is {@link LifecycleAuthority#ALLOWED}: {@code CPQ.User}, {@code DealDesk}, {@code SalesVP}, and
 * {@code CFO}. {@code Admin} is not in that set. A missing or unknown role is not in it either, so the matcher
 * fails closed. The baseline chain still returns 401 when there is no token.
 */
@Configuration
public class QuoteLineBulkRemoveAuthorization {

    @Bean
    OpenCrmSecurityCustomizer quoteLineBulkRemoveEditors() {
        return rules -> rules
                .requestMatchers(HttpMethod.POST, "/api/v1/quotes/*/line-items/bulk-remove")
                .hasAnyAuthority(LifecycleAuthority.ALLOWED.toArray(String[]::new));
    }
}
