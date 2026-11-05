package com.opencrm.order.service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.opencrm.order.domain.OrderProblem;
/** Broker ACL must restrict this topic to the quote outbox producer; consumer scope allowlist is independently enforced. */
@Component
@ConditionalOnProperty(name="oms.snapshot-consumer-enabled",havingValue="true")
public class SnapshotInboxListener {
 private final OrderService service;
 private final ObjectMapper mapper;
 public SnapshotInboxListener(OrderService service,ObjectMapper mapper) {this.service=service;this.mapper=mapper;}
 @KafkaListener(topics="cpq.oms.quote-accepted.v1",groupId="order-management")
 public void receive(String payload) {
  var event=mapper.readTree(payload);
  if(!event.hasNonNull("snapshot") || !event.hasNonNull("snapshotHash")) throw OrderProblem.invalid("Invalid source event");
  service.acceptTrustedSnapshot(event.get("snapshot").toString(),event.get("snapshotHash").asText());
 }
}
