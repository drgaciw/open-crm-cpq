package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import lombok.experimental.UtilityClass;

/**
 * Dedupe key for quote acceptance (ADR-018 D6/D8).
 *
 * <p>Quote acceptance reaches crm-service on two channels during the D8 dual run: Redis {@code quote:accepted} and
 * Kafka {@code cpq.quote.status.accepted}. Both are mapped to a {@link QuoteAcceptedCrmMessage}, and
 * {@link QuoteAcceptedDealProcessor} claims {@link #dedupeKey} under {@link #CLAIM_NAMESPACE} in
 * {@code processed_events}, in the same transaction as the deal update. The first channel to arrive marks the deal WON;
 * the other finds the claim and skips.
 *
 * <p>The key is the quote UUID, not the Kafka {@code eventId}: the Redis message has no {@code eventId}, and a quote is
 * accepted once, so its UUID is the natural key on both channels. This replaced the Redis {@code SET NX} keys
 * {@code crm:quote-accepted:processed:*}, which lived outside the deal transaction and were lost with Redis.
 */
@UtilityClass
public class QuoteAcceptedIdempotency {

    /** {@code processed_events.consumer_group} value shared by the Redis and Kafka quote-accepted listeners. */
    public static final String CLAIM_NAMESPACE = "crm-quote-accepted";

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
}
