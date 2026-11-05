package com.opencrm.common.event;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.util.Map;
import java.util.Properties;

/**
 * Explicit opt-in factory policy; never changes an application's legacy listener factory.
 * A failing record gets {@link #MAX_DELIVERY_ATTEMPTS} deliveries, {@link #RETRY_INTERVAL_MS} apart.
 * After the last attempt it is published to {@code <original-topic>.DLT} and its offset is committed,
 * so one poison record cannot stall its partition. The DLT topic must exist or be auto-created.
 * This utility supplies no global error-handler bean and does not replace broker trust gates.
 */
public final class OmsKafkaListenerFactories {
    public static final String NAME="omsRetainingKafkaListenerContainerFactory";
    public static final String DLT_SUFFIX=".DLT";
    /** Total deliveries of one record before dead-lettering: the initial attempt plus four retries. */
    public static final int MAX_DELIVERY_ATTEMPTS=5;
    public static final long RETRY_INTERVAL_MS=1000L;
    private OmsKafkaListenerFactories() {}

    /** Bounded retry, then dead-letter publish and offset commit. Shared by every OMS listener policy. */
    public static DefaultErrorHandler boundedDeadLetterErrorHandler(KafkaOperations<?,?> template) {
        var recoverer=new DeadLetterPublishingRecoverer(template,(record,failure)->new TopicPartition(record.topic()+DLT_SUFFIX,-1));
        var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(RETRY_INTERVAL_MS,MAX_DELIVERY_ATTEMPTS-1L));
        // Treat every exception as retryable, so the bounded limit above is the only stop condition.
        handler.setClassifications(Map.of(),true);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(true);
        return handler;
    }

    public static ConcurrentKafkaListenerContainerFactory<Object,Object> retaining(ConsumerFactory<Object,Object> consumerFactory, KafkaOperations<?,?> template) {
        var factory=new ConcurrentKafkaListenerContainerFactory<Object,Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(boundedDeadLetterErrorHandler(template));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        // Per-container overrides preserve the shared consumer factory's broker/TLS configuration.
        // Never mutate its global configuration or allow auto-commit to bypass the retry policy.
        var overrides=new Properties();
        overrides.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,"false");
        overrides.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        overrides.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class.getName());
        factory.setContainerCustomizer(container-> {
            var merged=new Properties();
            merged.putAll(container.getContainerProperties().getKafkaConsumerProperties());
            merged.putAll(overrides);
            container.getContainerProperties().setKafkaConsumerProperties(merged);
        });
        return factory;
    }
}
