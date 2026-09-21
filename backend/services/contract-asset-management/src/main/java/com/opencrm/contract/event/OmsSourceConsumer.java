package com.opencrm.contract.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="oms.source-consumer-enabled",havingValue="true")
public class OmsSourceConsumer {
    private final ObjectMapper mapper; private final OmsSourceRegistry registry; private final OmsBrokerTrust trust;
    public OmsSourceConsumer(ObjectMapper mapper,OmsSourceRegistry registry,OmsBrokerTrust trust) {this.mapper=mapper;this.registry=registry;this.trust=trust;}
    @KafkaListener(topics="oms.quote-accepted.v1",groupId="contract-oms-source-v1",containerFactory=com.opencrm.common.event.OmsKafkaListenerFactories.NAME)
    public void accept(String payload) throws Exception {
        var root=mapper.readTree(payload);
        if(!root.hasNonNull("snapshot") || !root.hasNonNull("snapshotHash")) throw new IllegalArgumentException("Accepted source required");
        trust.requireScope(root.path("snapshot").path("scopeId").asText());
        registry.register(root.get("snapshot").toString(),root.get("snapshotHash").asText());
    }
}
