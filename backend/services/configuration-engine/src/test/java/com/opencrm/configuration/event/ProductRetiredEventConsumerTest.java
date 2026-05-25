package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.configuration.model.ConfigurationModelCache;
import com.opencrm.configuration.session.ConfigurationSession;
import com.opencrm.configuration.session.ConfigurationSessionRepository;
import com.opencrm.configuration.session.ConfigurationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductRetiredEventConsumerTest {

    @Mock
    private ConfigurationSessionRepository sessionRepository;

    @Mock
    private ConfigurationModelCache configurationModelCache;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductRetiredEventConsumer consumer;

    @Test
    public void testHandleProductRetired_performance() throws Exception {
        String productId = "PROD-123";
        String message = "{\"productId\":\"PROD-123\", \"reasonCode\":\"RETIRED\"}";

        ProductRetiredEvent event = new ProductRetiredEvent();
        event.setProductId(productId);
        event.setReasonCode("RETIRED");
        // event.setTimestamp(Instant.now());

        when(objectMapper.readValue(message, ProductRetiredEvent.class)).thenReturn(event);

        List<ConfigurationSession> sessions = new ArrayList<>();
        for (long i = 0; i < 10000; i++) {
            ConfigurationSession session = new ConfigurationSession();
            session.setId(i);
            session.setProductId(productId);
            session.setStatus(ConfigurationStatus.IN_PROGRESS);
            sessions.add(session);
        }

        when(sessionRepository.findByProductIdAndStatus(productId, ConfigurationStatus.IN_PROGRESS)).thenReturn(sessions);

        long startTime = System.nanoTime();
        consumer.handleProductRetired(message);
        long endTime = System.nanoTime();

        System.out.println("Execution time: " + (endTime - startTime) / 1_000_000.0 + " ms");

        // Assertions for correctness
        verify(configurationModelCache).invalidate(productId);

        // Check that session status was updated
        assertEquals(ConfigurationStatus.CANCELLED, sessions.get(0).getStatus());
        assertEquals("Product has been retired: RETIRED", sessions.get(0).getValidationErrors());

        // Verify we optimized to saveAll
        verify(sessionRepository, times(1)).saveAll(sessions);
        verify(sessionRepository, never()).save(any());
    }
}
