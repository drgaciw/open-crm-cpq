package com.opencrm.jev.transport;

import com.opencrm.jev.error.JevException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(2, 500, 5_000, () -> 0.0);

    @Test
    @DisplayName("retries transient failures up to the limit")
    void retriesTransientFailures() {
        JevException server = new JevException.Server("boom", 503);

        assertThat(policy.shouldRetry(server, 0)).isTrue();
        assertThat(policy.shouldRetry(server, 1)).isTrue();
        assertThat(policy.shouldRetry(server, 2)).isFalse();
    }

    @Test
    @DisplayName("never retries auth, bad request or invalid answers")
    void doesNotRetryPermanentFailures() {
        assertThat(policy.shouldRetry(new JevException.Authentication("no", 401), 0)).isFalse();
        assertThat(policy.shouldRetry(new JevException.BadRequest("no", 422), 0)).isFalse();
        assertThat(policy.shouldRetry(new JevException.InvalidAnswer("no"), 0)).isFalse();
    }

    @Test
    @DisplayName("Retry-After wins over backoff and is capped")
    void honoursRetryAfter() {
        assertThat(policy.delayMs(new JevException.RateLimit("slow", 2L), 0)).isEqualTo(2_000);
        assertThat(policy.delayMs(new JevException.RateLimit("slow", 60L), 0)).isEqualTo(5_000);
    }

    @Test
    @DisplayName("backs off exponentially when there is no Retry-After")
    void backsOffExponentially() {
        JevException limited = new JevException.RateLimit("slow", null);

        assertThat(policy.delayMs(limited, 0)).isEqualTo(500);
        assertThat(policy.delayMs(limited, 1)).isEqualTo(1_000);
        assertThat(policy.delayMs(limited, 10)).isEqualTo(5_000);
    }
}
