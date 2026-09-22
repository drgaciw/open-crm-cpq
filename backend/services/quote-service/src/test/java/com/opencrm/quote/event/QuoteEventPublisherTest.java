package com.opencrm.quote.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ADR-018 D8 transition step 1: the Redis {@code quote:accepted} publish happens after commit and can no longer fail
 * (or roll back) a quote acceptance.
 */
@DisplayName("QuoteEventPublisher: Redis publish after commit, best effort (ADR-018 D8)")
class QuoteEventPublisherTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final QuoteEventPublisher publisher =
            new QuoteEventPublisher(redis, new ObjectMapper().registerModule(new JavaTimeModule()));
    private final QuoteAcceptedCrmMessage message =
            QuoteAcceptedCrmMessage.builder().quoteNumber("Q-1").quoteUuid("uuid-1").dealExternalId("deal-1").build();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("inside a transaction nothing is published until afterCommit, and never on rollback")
    void publishesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishQuoteAccepted(message);

        verify(redis, never()).convertAndSend(anyString(), anyString());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(redis, never()).convertAndSend(anyString(), anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(redis).convertAndSend(eq(QuoteEventPublisher.QUOTE_ACCEPTED_CHANNEL), contains("\"quoteUuid\":\"uuid-1\""));
    }

    @Test
    @DisplayName("a Redis failure is logged, not thrown, so it cannot break or undo the acceptance")
    void redisFailureIsSwallowed() {
        doThrow(new RedisConnectionFailureException("redis down")).when(redis).convertAndSend(anyString(), anyString());

        assertThatCode(() -> publisher.publishQuoteAccepted(message)).doesNotThrowAnyException();
    }
}
