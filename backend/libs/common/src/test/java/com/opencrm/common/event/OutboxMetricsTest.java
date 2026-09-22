package com.opencrm.common.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("outbox metrics (ADR-018 D10)")
class OutboxMetricsTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    @DisplayName("gauges report the backlog and counters the relay outcomes")
    void gaugesAndCounters() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L);
        when(repository.findOldestCreatedAt(OutboxStatus.PENDING)).thenReturn(NOW.minusSeconds(90));
        OutboxBacklogProbe probe = new OutboxBacklogProbe(repository, Duration.ofMinutes(1), Clock.fixed(NOW, ZoneOffset.UTC));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(probe);
        metrics.bindTo(registry);

        metrics.published(new OutboxEvent());
        metrics.published(new OutboxEvent());
        metrics.attemptFailed(new OutboxEvent(), false);
        metrics.attemptFailed(new OutboxEvent(), true);

        assertThat(registry.get("opencrm.outbox.pending").gauge().value()).isEqualTo(5.0);
        assertThat(registry.get("opencrm.outbox.failed").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("opencrm.outbox.oldest.pending.age").gauge().value()).isEqualTo(90.0);
        assertThat(registry.get("opencrm.outbox.relay.attempts").tag("result", "published").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("opencrm.outbox.relay.attempts").tag("result", "failed").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("opencrm.outbox.relay.attempts").tag("result", "exhausted").counter().count()).isEqualTo(1.0);
        // Three gauges read one cached snapshot: one query of each kind.
        verify(repository, times(1)).countByStatus(OutboxStatus.PENDING);
    }
}
