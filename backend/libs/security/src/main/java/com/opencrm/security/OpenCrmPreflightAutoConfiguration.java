package com.opencrm.security;

import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Runs {@link OpenCrmIdentityPreflight} in every service, servlet and reactive, when the context starts: one summary
 * line always, and a refused start under the production marker when the identity configuration is incomplete.
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenCrmSecurityProperties.class)
public class OpenCrmPreflightAutoConfiguration {

    @Bean
    public OpenCrmIdentityPreflight.Summary openCrmIdentityPreflight(OpenCrmSecurityProperties properties,
                                                                     Environment environment) {
        return new OpenCrmIdentityPreflight.Summary(OpenCrmIdentityPreflight.check(properties, environment));
    }
}
