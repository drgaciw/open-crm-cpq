package com.opencrm.quote.quote;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.quote.lineitem.QuoteLineItem;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds versioned {@link QuoteAcceptedEvent} payloads for Kafka subscribers.
 */
final class QuoteAcceptedKafkaEventFactory {

    private static final String SOURCE_SERVICE = "quote-service";

    private QuoteAcceptedKafkaEventFactory() {
    }

    static QuoteAcceptedEvent from(Quote quote, List<QuoteLineItem> lineItems) {
        return from(quote, lineItems, null, null);
    }

    /**
     * Optional pass-through for S6. A null document id and a null unit map leave the event unchanged from
     * {@link #from(Quote, List)}. This method does not read a tenant from the quote.
     */
    static QuoteAcceptedEvent from(Quote quote, List<QuoteLineItem> lineItems,
            String amendmentDocumentId, Map<String, Integer> applicationUnitNoByLineUuid) {
        AcceptedRampSnapshots.validatePairs(quote, lineItems);
        String quoteId = quote.getUuid() != null ? quote.getUuid() : quote.getQuoteNumber();
        String correlationId = quote.getUuid() != null ? quote.getUuid() : UUID.randomUUID().toString();

        Map<String, String> configurationAttributes = Map.of(
                "region", nullToEmpty(quote.getRegion()),
                "subscriptionType", nullToEmpty(quote.getSubscriptionType()),
                "lineCount", String.valueOf(lineItems.size())
        );

        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId(quoteId)
                        .quoteNumber(quote.getQuoteNumber())
                        .acceptedAt(quote.getAcceptedAt())
                        .build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder()
                        .customerId(quote.getCustomerExternalId())
                        .build(),
                QuoteAcceptedEvent.PricingSnapshot.builder()
                        .totalAmount(quote.getTotalAmount())
                        .currencyCode(quote.getCurrency())
                        .arr(quote.getArr())
                        .tcv(quote.getTcv())
                        .acv(quote.getAcv())
                        .exitArr(quote.getExitArr())
                        .build(),
                QuoteAcceptedEvent.ConfigurationSnapshot.builder()
                        .configurationId(extractConfigurationId(quote.getMetadata()))
                        .committedVersion("v1")
                        .attributes(configurationAttributes)
                        .build(),
                QuoteAcceptedEvent.TermSnapshot.builder()
                        .termMonths(quote.getDurationInMonths())
                        .startDate(quote.getStartDate() != null ? quote.getStartDate().toString() : null)
                        .endDate(quote.getEndDate() != null ? quote.getEndDate().toString() : null)
                        .build(),
                QuoteAcceptedEvent.CorrelationSnapshot.builder()
                        .correlationId(correlationId)
                        .causationId("quote-accept:" + quote.getQuoteNumber())
                        .sourceService(SOURCE_SERVICE)
                        .build()
        );
        // ADR-018 D8: the fields crm-service needs to mark the deal WON without the Redis quote:accepted message.
        if (quote.getDealExternalId() != null || quote.getDealType() != null) {
            event.setDeal(QuoteAcceptedEvent.DealSnapshot.builder()
                    .dealExternalId(quote.getDealExternalId())
                    .dealType(quote.getDealType() != null ? quote.getDealType().name() : null)
                    .build());
        }
        // What was sold, line by line, in the quote's order. Additive on v1: an empty list means a quote with no
        // lines, which is different from a missing field.
        event.setLineItems(lineItems.stream()
                .sorted(Comparator.comparingInt(QuoteLineItem::getSortOrder))
                .map(line -> lineItemSnapshot(quote, line, applicationUnitNoByLineUuid))
                .toList());
        event.setAmendmentDocumentId(blankToNull(amendmentDocumentId));
        if (quote.getTargetSubscriptionNumber() != null) {
            if (quote.getDealType() == DealType.NEW || quote.getObservedRevision() == null || quote.getObservedRevision() < 0
                    || quote.getSubscriptionEffectiveDate() == null) {
                throw new com.opencrm.common.exception.BusinessException("Incomplete typed subscription change", "SUBSCRIPTION_CHANGE_INVALID");
            }
            if (quote.getDealType() == DealType.AMENDMENT && lineItems.stream().filter(AcceptedRampSnapshots::recurring)
                    .anyMatch(line -> line.getSourceSubscriptionLineUuid() == null || line.getSourceSubscriptionLineUuid().isBlank())) {
                throw new com.opencrm.common.exception.BusinessException("Amendment requires an accepted source line identity", "SUBSCRIPTION_CHANGE_INVALID");
            }
            event.setSubscriptionChange(QuoteAcceptedEvent.SubscriptionChangeSnapshot.builder()
                    .targetSubscriptionNumber(quote.getTargetSubscriptionNumber()).observedRevision(quote.getObservedRevision())
                    .effectiveDate(quote.getSubscriptionEffectiveDate().toString()).build());
        }
        return event;
    }

    private static QuoteAcceptedEvent.LineItemSnapshot lineItemSnapshot(Quote quote, QuoteLineItem lineItem,
            Map<String, Integer> applicationUnitNoByLineUuid) {
        return QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid(lineItem.getUuid())
                .productExternalId(lineItem.getProductExternalId())
                .productName(lineItem.getProductName())
                .productType(lineItem.getProductType())
                .lineItemType(lineItem.getLineItemType())
                .quantity(lineItem.getQuantity())
                .unitOfMeasure(lineItem.getUnitOfMeasure())
                .listPrice(lineItem.getListPrice())
                .unitPrice(lineItem.getUnitPrice())
                .discountRate(lineItem.getDiscountRate())
                .discountAmount(lineItem.getDiscountAmount())
                .netPrice(lineItem.getNetPrice())
                .extendedAmount(lineItem.getExtendedAmount())
                .billingFrequency(lineItem.getBillingFrequency())
                .sortOrder(lineItem.getSortOrder())
                .ramp(AcceptedRampSnapshots.periods(quote, lineItem))
                .sourceSubscriptionLineUuid(lineItem.getSourceSubscriptionLineUuid())
                .hybridMigration(AcceptedRampSnapshots.hybrid(lineItem) != null ? AcceptedRampSnapshots.hybrid(lineItem).snapshot() : null)
                .initialProration(AcceptedRampSnapshots.proration(quote, lineItem))
                .applicationUnitNo(applicationUnitNo(applicationUnitNoByLineUuid, lineItem.getUuid()))
                .build();
    }

    private static Integer applicationUnitNo(Map<String, Integer> applicationUnitNoByLineUuid, String lineUuid) {
        if (applicationUnitNoByLineUuid == null || lineUuid == null) {
            return null;
        }
        return applicationUnitNoByLineUuid.get(lineUuid);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String extractConfigurationId(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        return metadata;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
