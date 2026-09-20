package com.opencrm.order.execution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
/** Enabling ingress requires authenticated broker transport and operator-attested producer ACLs. */
@Configuration
public class MessagingTrustConfiguration {
    @Bean
    Object verifiedOrderMessagingBoundary(Environment environment) {
        boolean snapshot = environment.getProperty("oms.snapshot-consumer-enabled",Boolean.class,false);
        boolean callback = environment.getProperty("oms.execution.callback-enabled",Boolean.class,false);
        if (!snapshot && !callback) return new Object();
        if (!environment.getProperty("oms.messaging.producer-acl-verified",Boolean.class,false)
                || !"SASL_SSL".equals(environment.getProperty("spring.kafka.properties.security.protocol")))
            throw new IllegalStateException("OMS ingress requires SASL_SSL and verified producer broker ACLs");
        Set<String> principals = Arrays.stream(environment.getProperty("oms.messaging.trusted-producer-principals", "").split(","))
                .map(String::trim).collect(Collectors.toSet());
        if (snapshot && (!principals.contains("quote-service") || environment.getProperty("oms.trusted-source-scopes", "").isBlank()))
            throw new IllegalStateException("Snapshot ingress requires quote-service producer and source scopes");
        if (callback && (!principals.contains("contract-service") || environment.getProperty("oms.execution.allowed-scopes", "").isBlank()))
            throw new IllegalStateException("Activation ingress requires contract-service producer and source scopes");
        return new Object();
    }
}
