package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAcceptedIdempotencyTest {

    @Test
    void dedupeKey_prefersQuoteUuid() {
        QuoteAcceptedCrmMessage msg = QuoteAcceptedCrmMessage.builder()
                .quoteUuid("qu-1")
                .quoteNumber("Q-100")
                .build();
        assertThat(QuoteAcceptedIdempotency.dedupeKey(msg, "deal-1")).isEqualTo("qu-1");
    }

    @Test
    void dedupeKey_fallsBackToDealAndQuoteNumber() {
        QuoteAcceptedCrmMessage msg = QuoteAcceptedCrmMessage.builder()
                .quoteNumber("Q-200")
                .build();
        assertThat(QuoteAcceptedIdempotency.dedupeKey(msg, "deal-2")).isEqualTo("deal-2:Q-200");
    }

    @Test
    void redisKey_hasStablePrefix() {
        assertThat(QuoteAcceptedIdempotency.redisKey("abc"))
                .isEqualTo(QuoteAcceptedIdempotency.KEY_PREFIX + "abc");
    }

    @Test
    void ttl_isThirtyDays() {
        assertThat(QuoteAcceptedIdempotency.TTL.toDays()).isEqualTo(30);
    }
}
