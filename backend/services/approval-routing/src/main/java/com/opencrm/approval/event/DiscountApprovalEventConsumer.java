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
import java.util.Comparator;
import java.util.List;
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
 *
 * <p>Changed figures supersede (owner decision 9). An event with <em>different</em> figures for a line that already
 * has an open PENDING approval creates a new PENDING request and marks the open one
 * {@link ApprovalStatus#SUPERSEDED}, naming its replacement. Approvers therefore see one open request per quote line,
 * carrying the figures they are actually being asked to approve, and the replaced request cannot be approved by
 * mistake (approving or rejecting it answers 409 {@code APPROVAL_SUPERSEDED}). The replaced row stays as history. A
 * request somebody already decided is never touched: new figures do not change what was approved.
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

    @KafkaListener(topics = {KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, KafkaTopics.RAMP_APPROVAL_BINDING_CHANGED}, groupId = CONSUMER_GROUP)
    @Transactional
    public void handleDiscountApprovalRequired(ConsumerRecord<String, String> record) {
        if (!inbox.claim(CONSUMER_GROUP, record)) {
            log.info("Skipping already-processed discount approval event {}", KafkaInbox.eventIdOf(record));
            return;
        }
        DiscountApprovalRequiredEvent event = parse(record.value());
        if (KafkaTopics.RAMP_APPROVAL_BINDING_CHANGED.equals(record.topic()) && event.getPricingFingerprint() == null) {
            throw new NonRetryableEventException("Ramp binding contract requires a pricing fingerprint");
        }
        log.info("Received discount approval required event for quote: {}", event.getQuoteId());

        List<ApprovalRequest> open = openRequestsForLine(event);
        ApprovalRequest identical = latestIdentical(open, event);
        if (identical != null && open.size() == 1) {
            log.info("Skipping duplicate discount approval event for quote {} line {}: an identical request is PENDING",
                    event.getQuoteId(), event.getLineId());
            return;
        }
        if (identical != null) {
            // The current request already carries these figures; older open ones (a legacy duplicate, or figures
            // that have since been re-sent) are replaced by it.
            supersedeAll(open, identical, event);
            return;
        }

        String ruleTier = event.getPricingFingerprint() == null
                ? determineApprovalTier(event.getMarginDelta()) : event.getApprovalTier();
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId(event.getQuoteId())
                .lineId(event.getLineId())
                .approvalType(ApprovalType.DISCOUNT)
                .status(Boolean.FALSE.equals(event.getApprovalRequired()) ? ApprovalStatus.CANCELLED : ApprovalStatus.PENDING)
                .requestedDiscount(event.getRequestedDiscount())
                .marginDelta(event.getMarginDelta())
                .currencyCode(event.getCurrencyCode())
                .pricingFingerprint(event.getPricingFingerprint())
                .governingPeriod(event.getGoverningPeriod())
                // The consumer acts as this service (ADR-016 decision 6); the event carries no verified user.
                .requesterId(currentActor.self())
                .submittedAt(Instant.now())
                .approvalTier(ruleTier)
                .reason(event.getPricingFingerprint() == null
                        ? "Discount exceeds standard threshold" : event.getReason())
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);
        supersedeAll(open, saved, event);

        // Advisory shadow only (ADR-015): the rule above stays authoritative. Never throws, and runs
        // after this transaction commits, so it cannot cause a redelivery.
        if (saved.getStatus() == ApprovalStatus.PENDING) discountTierShadow.recordAfterCommit(saved.getId(), event, ruleTier);

        log.info("Created approval request for quote: {}", event.getQuoteId());
    }

    private DiscountApprovalRequiredEvent parse(String payload) {
        try {
            DiscountApprovalRequiredEvent event = objectMapper.readValue(payload, DiscountApprovalRequiredEvent.class);
            if (event == null || event.getQuoteId() == null || event.getMarginDelta() == null) {
                throw new NonRetryableEventException("Discount approval event lacks quoteId or marginDelta: " + payload);
            }
            if (event.getPricingFingerprint() != null && (!event.getPricingFingerprint().matches("[a-f0-9]{64}")
                    || event.getGoverningPeriod() == null || event.getGoverningPeriod() < 1
                    || !("SALES_VP".equals(event.getApprovalTier()) || "DEAL_DESK".equals(event.getApprovalTier())
                    || "CFO".equals(event.getApprovalTier())))) {
                throw new NonRetryableEventException("Invalid ramp approval binding");
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("Unreadable discount approval event", e);
        }
    }

    /** Open (PENDING) discount requests for the event's quote line, oldest first. */
    private List<ApprovalRequest> openRequestsForLine(DiscountApprovalRequiredEvent event) {
        return approvalRequestRepository
                .findByQuoteIdAndStatus(event.getQuoteId(), ApprovalStatus.PENDING).stream()
                .filter(open -> (open.getApprovalType() == ApprovalType.DISCOUNT && Objects.equals(open.getLineId(), event.getLineId())
                        && (event.getPricingFingerprint() != null || open.getPricingFingerprint() == null))
                        || (event.getPricingFingerprint() != null && open.getPricingFingerprint() != null))
                .sorted(Comparator.comparing(ApprovalRequest::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /** The newest open request whose figures equal the event's, or {@code null}. */
    private static ApprovalRequest latestIdentical(List<ApprovalRequest> open, DiscountApprovalRequiredEvent event) {
        ApprovalRequest latest = null;
        for (ApprovalRequest candidate : open) {
            boolean identicalRamp = event.getPricingFingerprint() != null
                    && event.getPricingFingerprint().equals(candidate.getPricingFingerprint());
            boolean identicalLegacy = event.getPricingFingerprint() == null && candidate.getPricingFingerprint() == null
                    && sameAmount(candidate.getRequestedDiscount(), event.getRequestedDiscount())
                    && sameAmount(candidate.getMarginDelta(), event.getMarginDelta())
                    && Objects.equals(candidate.getCurrencyCode(), event.getCurrencyCode());
            if (identicalRamp || identicalLegacy) {
                latest = candidate;
            }
        }
        return latest;
    }

    /** Marks every open request except {@code current} as superseded by it, and records why. */
    private void supersedeAll(List<ApprovalRequest> open, ApprovalRequest current,
                              DiscountApprovalRequiredEvent event) {
        for (ApprovalRequest older : open) {
            if (older == current) {
                continue;
            }
            older.supersede(current.getId());
            approvalRequestRepository.save(older);
            log.info("Approval request {} for quote {} line {} superseded by {}: discount {} margin delta {} {} -> "
                            + "discount {} margin delta {} {}",
                    older.getId(), event.getQuoteId(), event.getLineId(), current.getId(),
                    older.getRequestedDiscount(), older.getMarginDelta(), older.getCurrencyCode(),
                    event.getRequestedDiscount(), event.getMarginDelta(), event.getCurrencyCode());
        }
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
