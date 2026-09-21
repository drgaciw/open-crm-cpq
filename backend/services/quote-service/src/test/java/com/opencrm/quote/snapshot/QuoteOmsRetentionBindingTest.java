package com.opencrm.quote.snapshot;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import static org.junit.jupiter.api.Assertions.*;
class QuoteOmsRetentionBindingTest {
    @Test void revisionListenerExplicitlyUsesRetainingFactory() throws Exception {
        var listener=QuoteRevisionConsumer.class.getMethod("accept",String.class).getAnnotation(KafkaListener.class);
        assertEquals("omsRetainingKafkaListenerContainerFactory",listener.containerFactory());
    }
    @Test void namedFactoryCoexistsWithBootDefault() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration.class))
            .withUserConfiguration(QuoteOmsKafkaConfiguration.class)
            .withPropertyValues("spring.kafka.properties.security.protocol=SASL_SSL","spring.kafka.consumer.enable-auto-commit=true")
            .run(context->{
                assertTrue(context.containsBean("kafkaListenerContainerFactory"));
                assertTrue(context.containsBean("omsRetainingKafkaListenerContainerFactory"));
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
