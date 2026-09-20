package com.opencrm.contract.event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
/** Startup attestation gate; deployment must actually configure authenticated Kafka topic ACLs. */
@Component
public class OmsBrokerTrust {
    private final Set<String> scopes;
    public OmsBrokerTrust(@Value("${oms.activation-consumer-enabled:false}") boolean activation,
            @Value("${oms.source-consumer-enabled:false}") boolean source,
            @Value("${oms.trusted-producer-configured:false}") boolean attested,
            @Value("${spring.kafka.properties.security.protocol:}") String protocol,
            @Value("${oms.quote-producer-principal:}") String quotePrincipal,
            @Value("${oms.activation-producer-principal:}") String activationPrincipal,
            @Value("${oms.trusted-source-scopes:}") String scopes) {
        this.scopes=Set.copyOf(Arrays.stream(scopes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        if((activation || source) && (!attested || !"SASL_SSL".equals(protocol) || this.scopes.isEmpty()
            || quotePrincipal.isBlank() || (activation && activationPrincipal.isBlank())))
            throw new IllegalStateException("OMS consumers require attested authenticated broker topic ACLs and explicit producer principals/scopes");
    }
    public void requireScope(String scope) {
        if(!scopes.contains(scope)) throw new IllegalArgumentException("Untrusted source scope");
    }
}
