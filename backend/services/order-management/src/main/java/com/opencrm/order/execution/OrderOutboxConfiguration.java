package com.opencrm.order.execution;
import com.opencrm.common.event.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
/** Explicit bean construction avoids ConditionalOnBean ordering against Kafka auto-configuration. */
@Configuration
@ConditionalOnProperty(name="oms.outbox-relay.enabled",havingValue="true")
@EntityScan(basePackageClasses=OutboxEvent.class)
@EnableJpaRepositories(basePackageClasses=OutboxEventRepository.class)
@Import({OutboxDeliveryStore.class, OrderOutboxRelayConfiguration.class})
public class OrderOutboxConfiguration {}
