package com.opencrm.security.client;

import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * The outbound half of ADR-016 decision 5: this service's {@link ServiceTokenProvider} and the {@link OutboundAuth}
 * interceptors that every {@code RestClient} to another OpenCRM service takes.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(OpenCrmSecurityProperties.class)
public class OpenCrmOutboundAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenProvider openCrmServiceTokenProvider(OpenCrmSecurityProperties properties,
                                                            Environment environment) {
        return ServiceTokenProviders.create(properties, environment,
                OpenCrmSecurityAutoConfiguration.isProduction(environment));
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboundAuth openCrmOutboundAuth(ServiceTokenProvider serviceTokens, OpenCrmSecurityProperties properties,
                                            Environment environment) {
        boolean requireToken = OpenCrmSecurityAutoConfiguration.isProduction(environment)
                && OpenCrmSecurityAutoConfiguration.mode(properties, environment) == SecurityMode.JWT;
        return new OutboundAuth(serviceTokens, requireToken);
    }
}
