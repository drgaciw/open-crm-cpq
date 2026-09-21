package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmsKafkaListenerFactoriesTest {
    @Test void retainsBeyondTenFailuresWithoutCommitting() {
        var consumerFactory=mock(ConsumerFactory.class);
        var listener=OmsKafkaListenerFactories.retaining(consumerFactory).createContainer("oms-source");
        var handler=listener.getCommonErrorHandler();
        var consumer=mock(Consumer.class);
        for(int attempt=0;attempt<25;attempt++)
            assertFalse(handler.handleOne(new IllegalStateException("temporary rejection"),new ConsumerRecord<>("oms-source",0,0L,"key","value"),consumer,mock(MessageListenerContainer.class)),"retention attempt "+attempt);
        assertFalse(handler.isAckAfterHandle());
        assertEquals(ContainerProperties.AckMode.RECORD,listener.getContainerProperties().getAckMode());
        assertEquals("false",listener.getContainerProperties().getKafkaConsumerProperties().getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        verifyNoInteractions(consumer);
    }
    @Test void defaultFatalFailureCannotRecoverOrSkip() {
        var listener=OmsKafkaListenerFactories.retaining(mock(ConsumerFactory.class)).createContainer("oms-source");
        assertFalse(listener.getCommonErrorHandler().handleOne(new ClassCastException("poison envelope"),new ConsumerRecord<>("oms-source",0,0L,"key","bad"),mock(Consumer.class),mock(MessageListenerContainer.class)));
    }
}
