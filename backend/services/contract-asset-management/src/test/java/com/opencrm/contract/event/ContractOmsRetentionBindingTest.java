package com.opencrm.contract.event;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import static org.junit.jupiter.api.Assertions.*;
class ContractOmsRetentionBindingTest {
    @Test void sourceAndActivationExplicitlyUseRetainingFactory() throws Exception {
        for(Class<?> type:new Class<?>[]{OmsSourceConsumer.class,OmsActivationConsumer.class}) {
            var listener=type.getMethod("accept",String.class).getAnnotation(KafkaListener.class);
            assertEquals("omsRetainingKafkaListenerContainerFactory",listener.containerFactory(),type.getSimpleName());
        }
    }
    @Test void legacyConsumerAndDefaultFactoryKeepExistingPolicy() throws Exception {
        assertEquals("",QuoteAcceptedEventConsumer.class.getMethod("handleQuoteAccepted",org.apache.kafka.clients.consumer.ConsumerRecord.class).getAnnotation(KafkaListener.class).containerFactory());
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration.class))
            .withUserConfiguration(ContractOmsKafkaConfiguration.class)
            .withPropertyValues("spring.kafka.properties.security.protocol=SASL_SSL","spring.kafka.consumer.enable-auto-commit=true")
            .run(context->{
                var named=context.getBean("omsRetainingKafkaListenerContainerFactory",org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory.class);
                var legacy=context.getBean("kafkaListenerContainerFactory",org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory.class);
                assertNotSame(named,legacy);
                assertSame(legacy.getConsumerFactory(),named.getConsumerFactory());
                assertEquals("SASL_SSL",named.getConsumerFactory().getConfigurationProperties().get("security.protocol"));
                assertEquals(true,legacy.getConsumerFactory().getConfigurationProperties().get("enable.auto.commit"));
                assertEquals("false",named.createContainer("oms").getContainerProperties().getKafkaConsumerProperties().getProperty("enable.auto.commit"));
                assertNotNull(named.createContainer("oms").getCommonErrorHandler());
                assertNull(legacy.createContainer("legacy").getCommonErrorHandler());
            });
    }
}
