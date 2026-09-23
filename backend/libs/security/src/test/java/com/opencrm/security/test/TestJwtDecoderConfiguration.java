package com.opencrm.security.test;

import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Replaces only the key source of the production decoder: tokens are verified against {@link TestTokens#publicKey()}
 * instead of the issuer's JWKS, and then validated by the production rules ({@link OpenCrmJwtDecoders#validator}:
 * timestamps, issuer, audience). The baseline's own decoder backs off because of {@code @ConditionalOnMissingBean}.
 */
@Configuration(proxyBeanMethods = false)
public class TestJwtDecoderConfiguration {

    @Bean
    public JwtDecoder testKeyJwtDecoder(OpenCrmSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties.getJwt()));
        return decoder;
    }
}
