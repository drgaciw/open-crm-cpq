package com.opencrm.quote.snapshot;
import com.opencrm.common.event.OmsKafkaListenerFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;

/** Named opt-in only: does not replace Boot's default listener factory or error policy. */
@Configuration(proxyBeanMethods=false)
public class QuoteOmsKafkaConfiguration {
    @Bean(name=OmsKafkaListenerFactories.NAME)
    public ConcurrentKafkaListenerContainerFactory<Object,Object> omsRetainingKafkaListenerContainerFactory(ConsumerFactory<Object,Object> consumerFactory, KafkaOperations<?,?> kafkaTemplate) {
        return OmsKafkaListenerFactories.retaining(consumerFactory, kafkaTemplate);
    }
}
