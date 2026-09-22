package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("outbox health indicator (ADR-018 D1/D2)")
class OutboxHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxBacklogProbe probe =
            new OutboxBacklogProbe(repository, Duration.ZERO, Clock.fixed(NOW, ZoneOffset.UTC));

    private void backlog(long pending, long failed, Instant oldestPending) {
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(pending);
        when(repository.countByStatus(OutboxStatus.FAILED)).thenReturn(failed);
        when(repository.findOldestCreatedAt(OutboxStatus.PENDING)).thenReturn(oldestPending);
    }

    private Health health(boolean relayRunning) {
        return new OutboxHealthIndicator(probe, () -> relayRunning, MAX_AGE).health();
    }

    @Test
    @DisplayName("relay running and an aged pending row: DOWN")
    void agedPendingRowIsDown() {
        backlog(3, 0, NOW.minus(Duration.ofMinutes(11)));

        Health health = health(true);

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("pending", 3L).containsEntry("oldestPendingAge", "PT11M");
    }

    @Test
    @DisplayName("relay running and a FAILED row: DOWN")
    void failedRowIsDown() {
        backlog(0, 1, null);

        assertThat(health(true).getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("relay running and a fresh backlog: UP")
    void freshBacklogIsUp() {
        backlog(2, 0, NOW.minusSeconds(30));

        assertThat(health(true).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("relay disabled: UNKNOWN with the backlog in the details, so probes are not broken")
    void relayDisabledIsUnknown() {
        backlog(42, 0, NOW.minus(Duration.ofDays(3)));

        Health health = health(false);

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails())
                .containsEntry("pending", 42L)
                .containsEntry("relay", "disabled (opencrm.outbox.relay.enabled=false)");
    }
}
