package com.opencrm.quote.quote;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.quote.lineitem.QuoteLineItem;

import java.util.Comparator;
import java.util.List;

final class QuoteAcceptedCrmMessageFactory {

    private QuoteAcceptedCrmMessageFactory() {
    }

    static QuoteAcceptedCrmMessage from(Quote quote, List<QuoteLineItem> lineItems) {
        String dealType = quote.getDealType() != null ? quote.getDealType().name() : null;
        String approvalState = quote.getApprovalState() != null ? quote.getApprovalState().name() : null;

        return QuoteAcceptedCrmMessage.builder()
                .quoteNumber(quote.getQuoteNumber())
                .quoteUuid(quote.getUuid())
                .customerExternalId(quote.getCustomerExternalId())
                .dealExternalId(quote.getDealExternalId())
                .dealType(dealType)
                .totalAmount(quote.getTotalAmount())
                .arr(quote.getArr())
                .tcv(quote.getTcv())
                .acv(quote.getAcv())
                .currency(quote.getCurrency())
                .durationInMonths(quote.getDurationInMonths())
                .acceptedAt(quote.getAcceptedAt())
                .customer(QuoteAcceptedCrmMessage.CustomerSnapshot.builder()
                        .customerExternalId(quote.getCustomerExternalId())
                        .build())
                .deal(QuoteAcceptedCrmMessage.DealSnapshot.builder()
                        .dealExternalId(quote.getDealExternalId())
                        .dealType(dealType)
                        .build())
                .configuration(QuoteAcceptedCrmMessage.ConfigurationSnapshot.builder()
                        .region(quote.getRegion())
                        .subscriptionType(quote.getSubscriptionType())
                        .metadata(quote.getMetadata())
                        .build())
                .pricing(QuoteAcceptedCrmMessage.PricingSnapshot.builder()
                        .subtotal(quote.getSubtotal())
                        .totalDiscount(quote.getTotalDiscount())
                        .totalTax(quote.getTotalTax())
                        .totalAmount(quote.getTotalAmount())
                        .arr(quote.getArr())
                        .tcv(quote.getTcv())
                        .acv(quote.getAcv())
                        .currency(quote.getCurrency())
                        .approvalState(approvalState)
                        .approvalTier(quote.getApprovalTier())
                        .pricingWaterfallJson(quote.getPricingWaterfallJson())
                        .build())
                .terms(QuoteAcceptedCrmMessage.TermsSnapshot.builder()
                        .durationInMonths(quote.getDurationInMonths())
                        .startDate(quote.getStartDate())
                        .endDate(quote.getEndDate())
                        .notes(quote.getNotes())
                        .build())
                .lineItems(lineItems.stream()
                        .sorted(Comparator.comparingInt(QuoteLineItem::getSortOrder))
                        .map(QuoteAcceptedCrmMessageFactory::lineItemSnapshot)
                        .toList())
                .build();
    }

    private static QuoteAcceptedCrmMessage.LineItemSnapshot lineItemSnapshot(QuoteLineItem lineItem) {
        return QuoteAcceptedCrmMessage.LineItemSnapshot.builder()
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
                .pricingExplanationCode(lineItem.getPricingExplanationCode())
                .pricingWaterfallJson(lineItem.getPricingWaterfallJson())
                .metadata(lineItem.getMetadata())
                .build();
    }
}
