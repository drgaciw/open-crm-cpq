package com.opencrm.contract.event;
import com.opencrm.common.event.OmsKafkaListenerFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

/** OMS source and activation retention; legacy quote-accepted delivery policy is unchanged. */
@Configuration(proxyBeanMethods=false)
public class ContractOmsKafkaConfiguration {
    @Bean(name=OmsKafkaListenerFactories.NAME)
    public ConcurrentKafkaListenerContainerFactory<Object,Object> omsRetainingKafkaListenerContainerFactory(ConsumerFactory<Object,Object> consumerFactory) {
        return OmsKafkaListenerFactories.retaining(consumerFactory);
    }
}
