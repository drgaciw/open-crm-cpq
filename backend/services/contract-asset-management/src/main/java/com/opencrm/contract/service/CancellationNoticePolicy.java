package com.opencrm.contract.service;

import com.opencrm.contract.domain.Subscription;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global cancellation notice period for DRG-472 slice a (AC2 only).
 *
 * <p>The guard is filled in after the failing tests. This type exists so those tests compile.
 * DRG-596 (In Review) states no global integer, so the configured default is 0 and a zero
 * period must leave existing cancel callers unchanged. A null {@code endDate} (evergreen)
 * skips the guard. There is no per-contract override and no effective-date cancel in this slice.
 */
@Component
public class CancellationNoticePolicy {

    public static final String ERROR_CODE = "NOTICE_PERIOD_ELAPSED";

    private final int noticeDays;
    private final Clock clock;

    @Autowired
    public CancellationNoticePolicy(
            @Value("${opencrm.contract.cancellation.notice-days:0}") int noticeDays) {
        this(noticeDays, Clock.systemUTC());
    }

    CancellationNoticePolicy(int noticeDays, Clock clock) {
        this.noticeDays = noticeDays;
        this.clock = clock;
    }

    public int noticeDays() {
        return noticeDays;
    }

    public void enforce(Subscription subscription) {
        // Red phase: the notice comparison is intentionally absent.
        if (subscription == null || clock == null || noticeDays < 0) {
            return;
        }
    }
}
