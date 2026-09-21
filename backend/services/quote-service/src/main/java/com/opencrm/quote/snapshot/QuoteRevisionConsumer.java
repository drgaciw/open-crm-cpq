package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
/** Broker ACLs must bind the declared OMS principal to this topic. No payload claim substitutes for broker authentication. */
@Component @ConditionalOnProperty(name="oms.quote.revision-consumer-enabled",havingValue="true")
public class QuoteRevisionConsumer {
    private final ObjectMapper mapper; private final QuoteRevisionService service;
    public QuoteRevisionConsumer(ObjectMapper mapper,QuoteRevisionService service,
            @Value("${oms.quote.revision-trusted-producer-configured:false}") boolean attested,
            @Value("${spring.kafka.properties.security.protocol:}") String protocol,
            @Value("${oms.quote.revision-producer-principal:}") String principal,
            @Value("${oms.quote.scope-id:}") String scope) {
        if(!attested || !"SASL_SSL".equals(protocol) || principal.isBlank() || scope.isBlank())
            throw new IllegalStateException("Quote revision consumer requires authenticated broker ACL attestation, OMS principal and scope");
        this.mapper=mapper;this.service=service;
    }
    @KafkaListener(topics="oms.quote-revision-requested.v1",groupId="quote-oms-revision-v1",
        containerFactory=com.opencrm.common.event.OmsKafkaListenerFactories.NAME,
        properties={"enable.auto.commit=false"})
    public void accept(String payload) throws Exception {service.reopen(mapper.readValue(payload,QuoteRevisionRequest.class));}
}
