package com.opencrm.contract.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.domain.Subscription;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Global cancellation notice period (DRG-472 slice a, acceptance criterion 2 only).
 *
 * <p>Policy source is DRG-596 (In Review, RA-62, call 2026-10-16): one global default, with a
 * per-contract override deferred to a later slice. DRG-596 does not state a number
 * (OQ-596-NOTICE leaves the integer unset), so {@code opencrm.contract.cancellation.notice-days}
 * defaults to {@code 0}. At 0 this guard does not run and existing cancel callers are unchanged.
 * CPQ Decision Q-472a-sem (2026-11-11) is option (a): guard only. A successful cancel still sets
 * {@code CANCELLED} immediately. This slice does not store an effective date, does not read a
 * per-contract override, and does not emit {@code SubscriptionCancelled}.
 *
 * <p>Null {@code endDate} is an evergreen subscription: the guard is skipped and cancel is not
 * rejected. The column is still non-null in the current schema; the skip is for callers that
 * have no end date in memory.
 *
 * <p>When {@code noticeDays} is positive and {@code endDate} is present, cancel is rejected with
 * {@link #ERROR_CODE} ({@code 400}) when {@code today + noticeDays} is strictly after
 * {@code endDate}. The exact boundary day ({@code today + noticeDays == endDate}) is allowed.
 * The renewal-automation notice record (DRG-596 AC-596-2, inclusive window) is a different clock
 * and is not written here.
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
        if (noticeDays < 0) {
            throw new IllegalArgumentException("opencrm.contract.cancellation.notice-days must be >= 0");
        }
        this.noticeDays = noticeDays;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int noticeDays() {
        return noticeDays;
    }

    /**
     * @throws BusinessException {@link #ERROR_CODE} when the global notice window has elapsed
     */
    public void enforce(Subscription subscription) {
        if (noticeDays == 0 || subscription.getEndDate() == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (today.plusDays(noticeDays).isAfter(subscription.getEndDate())) {
            throw new BusinessException(
                    "Cancellation notice period has elapsed",
                    HttpStatus.BAD_REQUEST,
                    ERROR_CODE);
        }
    }
}
