package com.opencrm.quote.lineitem;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleViews;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
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
    private final RampScheduleAttacher rampScheduleAttacher;
    private final com.opencrm.quote.ramp.RampScheduleGuard rampScheduleGuard;
    @PersistenceContext private EntityManager entityManager;

    /** Every line write participates in the quote version used by atomic ramp extension. */
    private void advanceQuoteVersion(Quote quote) {
        entityManager.lock(quote, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    private static final Set<QuoteStatus> EDITABLE_STATUSES = Set.of(QuoteStatus.DRAFT, QuoteStatus.REJECTED);

    public QuoteLineItemDto addLineItem(Long quoteId, LineItemCreateRequest request) {
        log.debug("Adding line item to quote ID: {}", quoteId);

        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (!canEdit(quote)) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        RampScheduleAttacher.PreparedRampSchedule prepared = rampScheduleAttacher.prepare(quote, request);
        advanceQuoteVersion(quote);
        String previousAuthoring = authoringFingerprint(quote);

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
                .sourceSubscriptionLineUuid(request.getSourceSubscriptionLineUuid())
                .hybridMigrationJson(com.opencrm.quote.ramp.RampPricingSnapshot.hybridJson(request.getHybridMigration()))
                .build();
        rampScheduleAttacher.attach(item, prepared);

        QuoteLineItem saved = lineItemRepository.save(item);
        rampScheduleGuard.invalidateOnEdit(quote, previousAuthoring);
        recalculateQuoteTotals(quote);
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

        if (!canEdit(quote)) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        QuoteLineItem item = lineItemRepository.findByIdAndDeletedFalse(lineItemId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        if (!item.getQuote().getId().equals(quoteId)) {
            throw new BusinessException("Line item " + lineItemId + " does not belong to quote " + quoteId, "LINE_ITEM_QUOTE_MISMATCH");
        }

        advanceQuoteVersion(quote);
        String previousAuthoring = authoringFingerprint(quote);
        item.softDelete();
        lineItemRepository.save(item);
        rampScheduleGuard.invalidateOnEdit(quote, previousAuthoring);
        recalculateQuoteTotals(quote);
        log.info("Removed line item {} from quote {}", lineItemId, quote.getQuoteNumber());
    }

    public QuoteLineItemDto updateLineItem(Long quoteId, Long lineItemId, LineItemCreateRequest request) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (!canEdit(quote)) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }

        QuoteLineItem item = lineItemRepository.findByIdAndDeletedFalse(lineItemId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        if (!item.getQuote().getId().equals(quoteId)) {
            throw new BusinessException("Line item " + lineItemId + " does not belong to quote " + quoteId, "LINE_ITEM_QUOTE_MISMATCH");
        }

        RampScheduleAttacher.PreparedRampSchedule prepared = rampScheduleAttacher.prepare(quote, request);
        advanceQuoteVersion(quote);
        String previousAuthoring = authoringFingerprint(quote);

        item.setProductExternalId(request.getProductExternalId());
        item.setProductName(request.getProductName());
        item.setProductType(request.getProductType());
        item.setLineItemType(request.getLineItemType());
        item.setQuantity(request.getQuantity());
        item.setUnitOfMeasure(request.getUnitOfMeasure());
        item.setListPrice(request.getListPrice());
        item.setBillingFrequency(request.getBillingFrequency());
        item.setSortOrder(request.getSortOrder());
        item.setNotes(request.getNotes());
        item.setSourceSubscriptionLineUuid(request.getSourceSubscriptionLineUuid());
        item.setHybridMigrationJson(com.opencrm.quote.ramp.RampPricingSnapshot.hybridJson(request.getHybridMigration()));
        rampScheduleAttacher.attach(item, prepared);

        boolean unchangedBoundAuthoring = com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote) != null
                && previousAuthoring.equals(authoringFingerprint(quote));
        if (!unchangedBoundAuthoring) {
            item.setUnitPrice(request.getListPrice());
            item.setNetPrice(request.getListPrice());
            item.setExtendedAmount(request.getListPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
            item.setPricingWaterfallJson(null);
        }

        QuoteLineItem saved = lineItemRepository.save(item);
        if (!unchangedBoundAuthoring) {
            rampScheduleGuard.invalidateOnEdit(quote, previousAuthoring);
            recalculateQuoteTotals(quote);
        }
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
        if (items.stream().anyMatch(RampScheduleViews::hasSteps)
                || com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote) != null) {
            quote.setRampScheduleStale(true);
        } else {
            quote.setRampScheduleStale(false);
        }
        quoteRepository.save(quote);
    }

    /** Save only the selected schedule and quote term in the caller's transaction. */
    public Quote persistExtendedRamp(Long quoteId, Long lineItemId, int durationInMonths,
                                   Long expectedQuoteVersion, com.opencrm.quote.ramp.RampScheduleDto schedule) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        if (!canEdit(quote)) {
            throw new BusinessException("Cannot modify line items in " + quote.getStatus() + " status", "QUOTE_NOT_EDITABLE");
        }
        if (!java.util.Objects.equals(expectedQuoteVersion, quote.getVersion())) {
            throw new BusinessException("Quote changed; reload before extending the term",
                    org.springframework.http.HttpStatus.CONFLICT, "QUOTE_VERSION_CONFLICT");
        }
        QuoteLineItem item = lineItemRepository.findByIdAndDeletedFalse(lineItemId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));
        if (!item.getQuote().getId().equals(quoteId)) {
            throw new BusinessException("Line item does not belong to quote", "LINE_ITEM_QUOTE_MISMATCH");
        }
        if (expectedQuoteVersion == null || durationInMonths < quote.getDurationInMonths()) {
            throw com.opencrm.quote.ramp.RampScheduleException.invalid(
                    List.of(com.opencrm.quote.ramp.RampReasons.TERM_MISMATCH));
        }
        // Validate against the proposed term before mutating any managed entity.
        var prepared = rampScheduleAttacher.prepare(Quote.builder().durationInMonths(durationInMonths).build(),
                LineItemCreateRequest.builder().schedule(schedule).quantity(item.getQuantity())
                        .billingFrequency(item.getBillingFrequency()).lineItemType(item.getLineItemType()).build());
        String previousAuthoring = authoringFingerprint(quote);
        try {
            // Lock and advance the root before touching any line row, including same-term catch-up.
            advanceQuoteVersion(quote);
            quote.setDurationInMonths(durationInMonths);
            rampScheduleAttacher.attach(item, prepared);
            item.setPricingWaterfallJson(null);
            lineItemRepository.save(item);
            // Invalidation reads can AUTO-flush dirty entities before the explicit flush.
            rampScheduleGuard.invalidateOnEdit(quote, previousAuthoring);
            // Every line is checked against the new term before calculate/submit can proceed.
            quote.setRampScheduleStale(true);
            return quoteRepository.saveAndFlush(quote);
        } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException conflict) {
            throw new BusinessException("Quote changed; reload before extending the term",
                    org.springframework.http.HttpStatus.CONFLICT, "QUOTE_VERSION_CONFLICT");
        }
    }

    private boolean canEdit(Quote quote) {
        return EDITABLE_STATUSES.contains(quote.getStatus()) || (quote.getStatus() == QuoteStatus.PENDING_APPROVAL
                && com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote) != null);
    }

    private String authoringFingerprint(Quote quote) {
        return com.opencrm.quote.ramp.RampPricingSnapshot.authoringFingerprint(quote,
                lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId()));
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
                .schedule(RampScheduleViews.toDto(item))
                .pricedPeriods(com.opencrm.quote.ramp.RampPricingSnapshot.pricedPeriods(item))
                .sourceSubscriptionLineUuid(item.getSourceSubscriptionLineUuid())
                .hybridMigration(com.opencrm.quote.ramp.RampPricingSnapshot.hybridMigration(item))
                .initialProration(com.opencrm.quote.ramp.RampPricingSnapshot.initialProration(item))
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .version(item.getVersion())
                .build();
    }
}
