package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.experimental.UtilityClass;

/**
 * Builds Redis keys for at-most-once processing of quote acceptance messages.
 */
@UtilityClass
public class QuoteAcceptedIdempotency {

    public static final String KEY_PREFIX = "crm:quote-accepted:processed:";

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
