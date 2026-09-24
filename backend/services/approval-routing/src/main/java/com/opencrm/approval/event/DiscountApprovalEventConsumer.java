package com.opencrm.approval.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Consumes discount approval required events and creates approval requests.
 *
 * <p>Idempotent (ADR-018 D6). A redelivered or re-sent event used to insert another PENDING approval every time. Now:
 * <ol>
 *   <li>the {@link KafkaInbox} refuses an event this group already processed (by {@code eventId} header, or delivery
 *       coordinates for records without one);</li>
 *   <li>as a natural-key backstop for producer duplicates without an {@code eventId}, an event whose figures match an
 *       open PENDING discount approval for the same quote line is skipped.</li>
 * </ol>
 * An event with <em>different</em> figures for a line that already has an open approval still creates a second one,
 * as before; whether it should supersede the open request is a business rule this class does not decide.
 *
 * <p>An unreadable payload is not retryable and goes straight to {@code cpq.discount.approval.required.DLT} (D7).
 *
 * <p>The requester is this service, {@code service:<client-id>} (ADR-016 decision 6), not a user named in the event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountApprovalEventConsumer {

    /** Kafka consumer group; also the inbox namespace. */
    public static final String CONSUMER_GROUP = "approval-routing";

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;
    private final DiscountTierShadowService discountTierShadow;
    private final KafkaInbox inbox;
    private final CurrentActor currentActor;

    @KafkaListener(topics = KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, groupId = CONSUMER_GROUP)
    @Transactional
    public void handleDiscountApprovalRequired(ConsumerRecord<String, String> record) {
        if (!inbox.claim(CONSUMER_GROUP, record)) {
            log.info("Skipping already-processed discount approval event {}", KafkaInbox.eventIdOf(record));
            return;
        }
        DiscountApprovalRequiredEvent event = parse(record.value());
        log.info("Received discount approval required event for quote: {}", event.getQuoteId());

        if (hasMatchingOpenRequest(event)) {
            log.info("Skipping duplicate discount approval event for quote {} line {}: an identical request is PENDING",
                    event.getQuoteId(), event.getLineId());
            return;
        }

        String ruleTier = determineApprovalTier(event.getMarginDelta());
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId(event.getQuoteId())
                .lineId(event.getLineId())
                .approvalType(ApprovalType.DISCOUNT)
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(event.getRequestedDiscount())
                .marginDelta(event.getMarginDelta())
                .currencyCode(event.getCurrencyCode())
                // The consumer acts as this service (ADR-016 decision 6); the event carries no verified user.
                .requesterId(currentActor.self())
                .submittedAt(Instant.now())
                .approvalTier(ruleTier)
                .reason("Discount exceeds standard threshold")
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);

        // Advisory shadow only (ADR-015): the rule above stays authoritative. Never throws, and runs
        // after this transaction commits, so it cannot cause a redelivery.
        discountTierShadow.recordAfterCommit(saved.getId(), event, ruleTier);

        log.info("Created approval request for quote: {}", event.getQuoteId());
    }

    private DiscountApprovalRequiredEvent parse(String payload) {
        try {
            DiscountApprovalRequiredEvent event = objectMapper.readValue(payload, DiscountApprovalRequiredEvent.class);
            if (event == null || event.getQuoteId() == null || event.getMarginDelta() == null) {
                throw new NonRetryableEventException("Discount approval event lacks quoteId or marginDelta: " + payload);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("Unreadable discount approval event", e);
        }
    }

    private boolean hasMatchingOpenRequest(DiscountApprovalRequiredEvent event) {
        return approvalRequestRepository
                .findByQuoteIdAndStatus(event.getQuoteId(), ApprovalStatus.PENDING).stream()
                .anyMatch(open -> open.getApprovalType() == ApprovalType.DISCOUNT
                        && Objects.equals(open.getLineId(), event.getLineId())
                        && sameAmount(open.getRequestedDiscount(), event.getRequestedDiscount())
                        && sameAmount(open.getMarginDelta(), event.getMarginDelta())
                        && Objects.equals(open.getCurrencyCode(), event.getCurrencyCode()));
    }

    /** Scale-insensitive: the columns are NUMERIC(19,2) and (19,4), the event carries whatever scale it has. */
    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private String determineApprovalTier(BigDecimal marginDelta) {
        // Simple tier logic - in production, this would be more sophisticated
        if (marginDelta.compareTo(BigDecimal.valueOf(30)) > 0) {
            return "VP_SALES";
        } else if (marginDelta.compareTo(BigDecimal.valueOf(20)) > 0) {
            return "DIRECTOR";
        } else {
            return "MANAGER";
        }
    }
}
