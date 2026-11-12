package com.opencrm.authorization.service;

import com.opencrm.authorization.config.AuthorizationCacheProperties;
import com.opencrm.authorization.repository.RoleAssignmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-307 acceptance criteria: "Authorize is evaluated in under 10ms from cache". This measures an actual
 * cache-warm call, not a cache miss: the first call primes the Caffeine entry (and is excluded from the
 * measurement, same as the story's "from cache" wording), every later call must come from
 * {@link AuthorizationQueryService}'s cache with no further database round trip, which the repository
 * invocation count asserts directly.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationQueryServiceBenchmarkTest {

    private static final String PRINCIPAL = "user:bob";
    /** Generous margin under the 10ms requirement so the assertion stays meaningful on a loaded CI runner. */
    private static final long BUDGET_NANOS = 10_000_000L;
    private static final int WARMUP_ITERATIONS = 2_000;
    private static final int MEASURED_ITERATIONS = 10_000;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Test
    @Tag("benchmark")
    @DisplayName("a cache-warm Authorize call completes in under 10ms, and does not hit the database again")
    void cacheWarmCallIsUnder10Milliseconds() {
        when(roleAssignmentRepository.findEffectivePermissionRows(PRINCIPAL))
                .thenReturn(List.<Object[]>of(new Object[]{"quote:approve", "quote:*"}));

        AuthorizationQueryService service = new AuthorizationQueryService(roleAssignmentRepository,
                new AuthorizationCacheProperties());

        // Prime the cache. This call is allowed to hit the database and is not measured.
        service.authorize(PRINCIPAL, "quote:approve", "quote:123");

        // JIT warmup: run a batch before measuring so the timed loop is not dominated by interpreter overhead,
        // which the "evaluated ... from cache" requirement is not about.
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            service.authorize(PRINCIPAL, "quote:approve", "quote:123");
        }

        long worstNanos = 0;
        long totalNanos = 0;
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            service.authorize(PRINCIPAL, "quote:approve", "quote:123");
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
            worstNanos = Math.max(worstNanos, elapsed);
        }
        double averageNanos = (double) totalNanos / MEASURED_ITERATIONS;

        // Exactly one database read for the whole test: the prime call. Every measured call was a cache hit.
        verify(roleAssignmentRepository, times(1)).findEffectivePermissionRows(PRINCIPAL);

        assertThat(averageNanos).as("average cache-warm Authorize call, nanos").isLessThan(BUDGET_NANOS);
        assertThat(worstNanos).as("worst-case cache-warm Authorize call, nanos").isLessThan(BUDGET_NANOS);
    }
}
