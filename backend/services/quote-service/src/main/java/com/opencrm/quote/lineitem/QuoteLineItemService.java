package com.opencrm.quote.lineitem;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteLineItemService {

    private final QuoteLineItemRepository lineItemRepository;
    private final QuoteRepository quoteRepository;

    private static final Set<QuoteStatus> EDITABLE_STATUSES = Set.of(QuoteStatus.DRAFT, QuoteStatus.REJECTED);

    public QuoteLineItemDto addLineItem(Long quoteId, LineItemCreateRequest request) {
        log.debug("Adding line item to quote ID: {}", quoteId);

        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (!EDITABLE_STATUSES.contains(quote.getStatus())) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        QuoteLineItem item = QuoteLineItem.builder()
                .quote(quote)
                .productExternalId(request.getProductExternalId())
                .productName(request.getProductName())
                .productType(request.getProductType())
                .lineItemType(request.getLineItemType())
                .quantity(request.getQuantity())
                .unitOfMeasure(request.getUnitOfMeasure())
                .listPrice(request.getListPrice())
                .unitPrice(request.getListPrice())
                .netPrice(request.getListPrice())
                .extendedAmount(request.getListPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                .billingFrequency(request.getBillingFrequency())
                .sortOrder(request.getSortOrder())
                .notes(request.getNotes())
                .build();

        quote.addLineItem(item);
        QuoteLineItem saved = lineItemRepository.save(item);
        quoteRepository.save(quote);
        log.info("Added line item {} to quote {}", saved.getId(), quote.getQuoteNumber());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<QuoteLineItemDto> getLineItems(Long quoteId) {
        return lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quoteId)
                .stream().map(this::toDto).toList();
    }

    public void removeLineItem(Long quoteId, Long lineItemId) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (!EDITABLE_STATUSES.contains(quote.getStatus())) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        QuoteLineItem item = lineItemRepository.findByIdAndDeletedFalse(lineItemId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        if (!item.getQuote().getId().equals(quoteId)) {
            throw new BusinessException("Line item " + lineItemId + " does not belong to quote " + quoteId, "LINE_ITEM_QUOTE_MISMATCH");
        }

        quote.removeLineItem(item);
        lineItemRepository.save(item);
        quoteRepository.save(quote);
        log.info("Removed line item {} from quote {}", lineItemId, quote.getQuoteNumber());
    }

    public QuoteLineItemDto updateLineItem(Long quoteId, Long lineItemId, LineItemCreateRequest request) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (!EDITABLE_STATUSES.contains(quote.getStatus())) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        QuoteLineItem item = lineItemRepository.findByIdAndDeletedFalse(lineItemId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        if (!item.getQuote().getId().equals(quoteId)) {
            throw new BusinessException("Line item " + lineItemId + " does not belong to quote " + quoteId, "LINE_ITEM_QUOTE_MISMATCH");
        }

        item.setProductExternalId(request.getProductExternalId());
        item.setProductName(request.getProductName());
        item.setProductType(request.getProductType());
        item.setLineItemType(request.getLineItemType());
        item.setQuantity(request.getQuantity());
        item.setUnitOfMeasure(request.getUnitOfMeasure());
        item.setListPrice(request.getListPrice());
        item.setUnitPrice(request.getListPrice());
        item.setNetPrice(request.getListPrice());
        item.setExtendedAmount(request.getListPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        item.setBillingFrequency(request.getBillingFrequency());
        item.setSortOrder(request.getSortOrder());
        item.setNotes(request.getNotes());

        QuoteLineItem saved = lineItemRepository.save(item);
        recalculateQuoteTotals(quote);
        log.info("Updated line item {} in quote {}", lineItemId, quote.getQuoteNumber());

        return toDto(saved);
    }

    private void recalculateQuoteTotals(Quote quote) {
        List<QuoteLineItem> items = lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId());

        BigDecimal subtotal = items.stream()
                .map(i -> i.getExtendedAmount() != null ? i.getExtendedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = items.stream()
                .map(i -> i.getDiscountAmount() != null ? i.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        quote.setSubtotal(subtotal);
        quote.setTotalDiscount(totalDiscount);
        quote.setTotalAmount(subtotal.subtract(totalDiscount));
        quoteRepository.save(quote);
    }

    private QuoteLineItemDto toDto(QuoteLineItem item) {
        return QuoteLineItemDto.builder()
                .id(item.getId())
                .uuid(item.getUuid())
                .productExternalId(item.getProductExternalId())
                .productName(item.getProductName())
                .productType(item.getProductType())
                .lineItemType(item.getLineItemType())
                .quantity(item.getQuantity())
                .unitOfMeasure(item.getUnitOfMeasure())
                .listPrice(item.getListPrice())
                .unitPrice(item.getUnitPrice())
                .discountRate(item.getDiscountRate())
                .discountAmount(item.getDiscountAmount())
                .netPrice(item.getNetPrice())
                .extendedAmount(item.getExtendedAmount())
                .billingFrequency(item.getBillingFrequency())
                .sortOrder(item.getSortOrder())
                .pricingExplanationCode(item.getPricingExplanationCode())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .version(item.getVersion())
                .build();
    }
}
