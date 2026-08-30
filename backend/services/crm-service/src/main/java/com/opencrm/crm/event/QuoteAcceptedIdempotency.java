package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.experimental.UtilityClass;

import java.time.Duration;

/**
 * Builds Redis keys for at-most-once processing of quote acceptance messages.
 * <p>
 * <b>Semantics (claim-then-apply, no poisoned keys):</b>
 * <ol>
 *   <li>Listener claims with {@code SET NX} + {@link #TTL} before calling the processor.</li>
 *   <li>Retain the key only after a successful deal update (deal found and marked WON).</li>
 *   <li>If the deal is missing (processor no-op) or apply throws, <b>delete</b> the key so a later
 *       republish can retry — do not leave a 30-day “processed” key for a skipped update.</li>
 *   <li>Duplicate delivery while the key exists skips re-application (true at-most-once after success).</li>
 * </ol>
 */
@UtilityClass
public class QuoteAcceptedIdempotency {

    public static final String KEY_PREFIX = "crm:quote-accepted:processed:";

    /** TTL for retained processed keys after a successful deal update. */
    public static final Duration TTL = Duration.ofDays(30);

    /**
     * Prefer quote UUID; otherwise fall back to deal id + quote number for deduplication.
     */
    public static String dedupeKey(QuoteAcceptedCrmMessage event, String dealExternalId) {
        if (event.getQuoteUuid() != null && !event.getQuoteUuid().isBlank()) {
            return event.getQuoteUuid();
        }
        String quotePart = event.getQuoteNumber() != null && !event.getQuoteNumber().isBlank()
                ? event.getQuoteNumber()
                : "unknown-quote";
        return dealExternalId + ":" + quotePart;
    }

    public static String redisKey(String dedupeKey) {
        return KEY_PREFIX + dedupeKey;
    }
}
