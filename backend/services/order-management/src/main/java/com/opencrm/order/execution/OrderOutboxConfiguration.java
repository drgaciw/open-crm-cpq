package com.opencrm.order.execution;
import com.opencrm.common.event.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
/** Explicit bean construction avoids ConditionalOnBean ordering against Kafka auto-configuration. */
@Configuration
@ConditionalOnProperty(name="oms.outbox-relay.enabled",havingValue="true")
@EntityScan(basePackageClasses=OutboxEvent.class)
@EnableJpaRepositories(basePackageClasses=OutboxEventRepository.class)
@Import(OutboxDeliveryStore.class)
public class OrderOutboxConfiguration {
    @Bean
    OutboxRelayPublisher orderOutboxRelay(OutboxEventRepository repository,KafkaTemplate<String,String> kafka,OutboxDeliveryStore store) {
        return new OutboxRelayPublisher(repository,kafka,store);
    }
}
