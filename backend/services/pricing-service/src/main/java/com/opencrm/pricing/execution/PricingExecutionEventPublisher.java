package com.opencrm.pricing.execution;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.dto.PriceCalculatedEvent;
import com.opencrm.common.event.dto.v1.PriceCalculatedEventV1;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes pricing outcomes to the transactional outbox using Stack 2A published-language DTOs.
 */
@Component
@RequiredArgsConstructor
public class PricingExecutionEventPublisher {

    static final BigDecimal DISCOUNT_APPROVAL_THRESHOLD = new BigDecimal("0.20");

    private final OutboxEventPublisher outboxEventPublisher;

    public void publishOutcomes(
            PricingContext context,
            PricingResult result,
            String pricebookVersion,
            String rulesetVersion
    ) {
        if (context == null || result == null || context.getQuoteExternalId() == null) {
            return;
        }

        String quoteId = context.getQuoteExternalId();
        String correlationId = context.getCorrelationId() != null
                ? context.getCorrelationId()
                : UUID.randomUUID().toString();

        Set<String> explanationCodes = new LinkedHashSet<>();
        if (result.getExplanationCodes() != null) {
            explanationCodes.addAll(result.getExplanationCodes());
        }

        FinancialMetrics metrics = result.getFinancialMetrics();
        BigDecimal subtotal = sumListPrice(result.getLineResults());
        BigDecimal discount = metrics != null && metrics.getTotalDiscount() != null
                ? metrics.getTotalDiscount()
                : BigDecimal.ZERO;
        BigDecimal total = metrics != null && metrics.getTcv() != null ? metrics.getTcv() : subtotal.subtract(discount);

        PriceCalculatedEvent priceCalculatedEvent = new PriceCalculatedEvent(
                quoteId,
                Instant.now(),
                result.isSuccess(),
                subtotal,
                discount,
                total,
                resolveCurrency(context, result),
                toLinePayloads(result.getLineResults()),
                new ArrayList<>(explanationCodes),
                result.getApprovalState(),
                result.getApprovalTier()
        );

        outboxEventPublisher.publish(
                "PriceCalculation",
                quoteId,
                KafkaTopics.PRICE_CALCULATED,
                priceCalculatedEvent.toPublishedLanguageV1()
        );

        if (requiresDiscountApproval(metrics)) {
            explanationCodes.add("DISCOUNT_APPROVAL_REQUIRED");
            BigDecimal marginDelta = metrics.getDiscountRate() != null
                    ? metrics.getDiscountRate().multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            DiscountApprovalRequiredEvent approvalEvent = new DiscountApprovalRequiredEvent(
                    quoteId,
                    firstLineId(result.getLineResults()),
                    marginDelta,
                    discount,
                    resolveCurrency(context, result)
            );

            outboxEventPublisher.publish(
                    "PriceCalculation",
                    quoteId,
                    KafkaTopics.DISCOUNT_APPROVAL_REQUIRED,
                    approvalEvent.toPublishedLanguageV1()
            );
        }
    }

    private boolean requiresDiscountApproval(FinancialMetrics metrics) {
        if (metrics == null || metrics.getDiscountRate() == null) {
            return false;
        }
        return metrics.getDiscountRate().compareTo(DISCOUNT_APPROVAL_THRESHOLD) > 0;
    }

    private static BigDecimal sumListPrice(List<PricingLineResult> lines) {
        if (lines == null) {
            return BigDecimal.ZERO;
        }
        return lines.stream()
                .map(line -> {
                    BigDecimal list = line.getListPrice() != null ? line.getListPrice() : BigDecimal.ZERO;
                    int qty = line.getQuantity() != null ? line.getQuantity() : 0;
                    return list.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String firstLineId(List<PricingLineResult> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return lines.get(0).getProductExternalId();
    }

    private static String resolveCurrency(PricingContext context, PricingResult result) {
        if (result.getLineResults() != null) {
            for (PricingLineResult line : result.getLineResults()) {
                if (line.getMetadata() != null && line.getMetadata().get("currency") != null) {
                    return String.valueOf(line.getMetadata().get("currency"));
                }
            }
        }
        return context.getCurrency() != null ? context.getCurrency() : "USD";
    }

    private static List<PriceCalculatedEventV1.PriceCalculatedLineV1> toLinePayloads(List<PricingLineResult> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(line -> PriceCalculatedEventV1.PriceCalculatedLineV1.builder()
                        .lineId(line.getProductExternalId())
                        .productExternalId(line.getProductExternalId())
                        .quantity(line.getQuantity())
                        .listPrice(line.getListPrice())
                        .finalPrice(line.getFinalPrice())
                        .build())
                .toList();
    }
}
