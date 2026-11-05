package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OmsKafkaListenerFactoriesTest {
    @Test void failingRecordIsRetriedBoundedTimesThenDeadLetteredAndCommitted() {
        KafkaOperations<Object,Object> template = mock(KafkaOperations.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var handler=OmsKafkaListenerFactories.boundedDeadLetterErrorHandler(template);
        var consumer=mock(Consumer.class);
        var container=mock(MessageListenerContainer.class);
        var record=new ConsumerRecord<>("oms.activation-requested.v1",0,7L,"key","value");
        for(int delivery=1;delivery<OmsKafkaListenerFactories.MAX_DELIVERY_ATTEMPTS;delivery++)
            assertFalse(handler.handleOne(new IllegalStateException("temporary rejection"),record,consumer,container),"retry before limit, delivery "+delivery);
        verify(template,never()).send(any(ProducerRecord.class));
        assertTrue(handler.handleOne(new IllegalStateException("temporary rejection"),record,consumer,container),"fifth delivery is recovered");
        var sent=org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template,times(1)).send(sent.capture());
        assertEquals("oms.activation-requested.v1"+OmsKafkaListenerFactories.DLT_SUFFIX,sent.getValue().topic());
        assertEquals("value",sent.getValue().value());
        assertFalse(handler.isAckAfterHandle());
    }

    @Test void boundedPolicyDefinesFiveDeliveriesOneSecondApart() {
        assertEquals(5,OmsKafkaListenerFactories.MAX_DELIVERY_ATTEMPTS);
        assertEquals(1000L,OmsKafkaListenerFactories.RETRY_INTERVAL_MS);
        assertEquals(".DLT",OmsKafkaListenerFactories.DLT_SUFFIX);
    }

    @Test void factoryKeepsAutoCommitDisabledOnContainer() {
        var consumerFactory=mock(ConsumerFactory.class);
        KafkaOperations<Object,Object> template=mock(KafkaOperations.class);
        var listener=OmsKafkaListenerFactories.retaining(consumerFactory,template).createContainer("oms-source");
        assertEquals(ContainerProperties.AckMode.RECORD,listener.getContainerProperties().getAckMode());
        assertEquals("false",listener.getContainerProperties().getKafkaConsumerProperties().getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    }
}
