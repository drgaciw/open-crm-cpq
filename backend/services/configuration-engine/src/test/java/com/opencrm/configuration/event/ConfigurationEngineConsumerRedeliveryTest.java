package com.opencrm.configuration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.configuration.model.ConfigurationModelCache;
import com.opencrm.configuration.session.ConfigurationSession;
import com.opencrm.configuration.session.ConfigurationSessionRepository;
import com.opencrm.configuration.session.ConfigurationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-018 D6: configuration-engine's consumers rely on natural idempotency instead of an inbox table, so a
 * redelivered event must leave the same state as a single delivery.
 */
@DisplayName("configuration-engine consumers are idempotent under redelivery (ADR-018 D6)")
class ConfigurationEngineConsumerRedeliveryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("product retired twice cancels each IN_PROGRESS session once")
    void productRetiredRedelivery() {
        List<ConfigurationSession> store = new ArrayList<>(List.of(session(), session()));
        ConfigurationSessionRepository repository = mock(ConfigurationSessionRepository.class);
        when(repository.findByProductIdAndStatus(eq("P-1"), eq(ConfigurationStatus.IN_PROGRESS)))
                .thenAnswer(call -> store.stream()
                        .filter(s -> s.getStatus() == ConfigurationStatus.IN_PROGRESS)
                        .collect(Collectors.toList()));
        List<Integer> savedBatchSizes = new ArrayList<>();
        when(repository.saveAll(anyList())).thenAnswer(call -> {
            List<?> batch = call.getArgument(0);
            savedBatchSizes.add(batch.size());
            return batch;
        });
        ProductRetiredEventConsumer consumer =
                new ProductRetiredEventConsumer(repository, new ConfigurationModelCache(), objectMapper);
        String event = "{\"productId\":\"P-1\",\"retiredAt\":\"2026-09-22T12:00:00Z\",\"reasonCode\":\"EOL\"}";

        consumer.handleProductRetired(event);
        consumer.handleProductRetired(event);

        assertThat(store).allMatch(s -> s.getStatus() == ConfigurationStatus.CANCELLED);
        assertThat(savedBatchSizes).containsExactly(2, 0);
    }

    @Test
    @DisplayName("catalog change twice leaves the cache as one delivery does")
    void catalogLifecycleRedelivery() {
        ConfigurationModelCache cache = new ConfigurationModelCache();
        ProductCatalogLifecycleEventConsumer consumer = new ProductCatalogLifecycleEventConsumer(cache, objectMapper);
        String event = "{\"aggregateId\":\"P-1\",\"changeType\":\"UPDATED\",\"changedAt\":\"2026-09-22T12:00:00Z\"}";

        consumer.handleProductUpdated(event);
        consumer.handleProductUpdated(event);

        assertThat(cache.get("P-1")).isEmpty();
    }

    private static ConfigurationSession session() {
        ConfigurationSession session = new ConfigurationSession();
        session.setProductId("P-1");
        session.setStatus(ConfigurationStatus.IN_PROGRESS);
        return session;
    }
}
