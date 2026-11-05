package com.opencrm.order.execution;
import com.opencrm.common.order.ActivationAcknowledgment;
import com.opencrm.order.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
/** Enabling this consumer requires broker ACLs allowing only contract-service to publish this topic. */
@Component
@ConditionalOnProperty(name="oms.execution.callback-enabled",havingValue="true")
public class ActivationAcknowledgmentListener {
    private final ExecutionService execution;
    private final ObjectMapper mapper;
    public ActivationAcknowledgmentListener(ExecutionService execution,ObjectMapper mapper) {this.execution=execution;this.mapper=mapper;}
    @KafkaListener(topics="oms.activation-acknowledged.v1",groupId="oms-activation-ack-v1")
    public void accept(String payload) {
        execution.acceptActivationAck(mapper.readValue(payload,ActivationAcknowledgment.class),OrderService.hash(payload));
    }
}
