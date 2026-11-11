package com.opencrm.quote.comparison;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteComparisonService {
    static final int MAX_ALTERNATIVES = 50;
    private final QuoteComparisonRepository quotes;
    private final QuotePrimarySelectionRepository selections;

    public QuoteComparisonDto compare(Long id) {
        UserContext user = user();
        Quote anchor = quotes.findByIdAndTenantIdAndDeletedFalse(id, user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        return comparison(anchor, user.getTenantId());
    }

    /** Seller preference only: no status, pricing, approval, acceptance or forecast mutation. Last committed selection wins. */
    @Transactional
    public QuoteComparisonDto markPrimary(Long id) {
        UserContext user = user();
        Quote anchor = quotes.lockAnchor(id, user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        // Validate the complete scope before a preference can be written.
        alternatives(anchor, user.getTenantId());
        selections.select(user.getTenantId(), anchor.getDealExternalId(), anchor.getId(), user.getUserId());
        return comparison(anchor, user.getTenantId());
    }

    private QuoteComparisonDto comparison(Quote anchor, String tenant) {
        List<Quote> alternatives = alternatives(anchor, tenant);
        Long primary = selections.selectedQuote(tenant, anchor.getDealExternalId())
                .filter(id -> alternatives.stream().anyMatch(quote -> Objects.equals(quote.getId(), id)))
                .orElse(null);
        boolean sameCurrency = alternatives.stream().map(Quote::getCurrency).allMatch(currency ->
                currency != null && !currency.isBlank() && Objects.equals(currency, anchor.getCurrency()));
        return new QuoteComparisonDto(anchor.getDealExternalId(), anchor.getCustomerExternalId(), primary,
                sameCurrency, alternatives.stream().map(this::alternative).toList());
    }

    private List<Quote> alternatives(Quote anchor, String tenant) {
        if (anchor.getDealExternalId() == null || anchor.getDealExternalId().isBlank()) {
            throw new BusinessException("Link this quote to an opportunity before comparing alternatives",
                    "QUOTE_COMPARISON_REQUIRES_OPPORTUNITY");
        }
        List<Quote> alternatives = quotes.alternatives(tenant, anchor.getDealExternalId(),
                PageRequest.of(0, MAX_ALTERNATIVES + 1));
        if (alternatives.size() > MAX_ALTERNATIVES) {
            throw new BusinessException("This opportunity has more than 50 alternatives; narrow the opportunity first",
                    "QUOTE_COMPARISON_LIMIT");
        }
        if (anchor.getCustomerExternalId() == null || anchor.getCustomerExternalId().isBlank()
                || alternatives.stream().anyMatch(quote -> !Objects.equals(
                        anchor.getCustomerExternalId(), quote.getCustomerExternalId()))) {
            throw new BusinessException("Opportunity alternatives must belong to the same customer",
                    "QUOTE_COMPARISON_CUSTOMER_MISMATCH");
        }
        return alternatives;
    }

    private QuoteComparisonDto.Alternative alternative(Quote quote) {
        List<QuoteComparisonDto.Line> lines = quote.getLineItems().stream().filter(line -> !line.isDeleted())
                .sorted(Comparator.comparingInt(QuoteLineItem::getSortOrder))
                .map(line -> new QuoteComparisonDto.Line(line.getProductExternalId(), line.getProductName(),
                        line.getProductType(), line.getLineItemType(), line.getQuantity(),
                        line.getUnitOfMeasure(), line.getBillingFrequency())).toList();
        return new QuoteComparisonDto.Alternative(quote.getId(), quote.getQuoteNumber(), quote.getName(),
                quote.getStatus(), quote.getDealType(), quote.getCurrency(), quote.getRegion(), quote.getDurationInMonths(),
                quote.getStartDate(), quote.getEndDate(), quote.getSubtotal(), quote.getTotalDiscount(), quote.getTotalTax(),
                quote.getTotalAmount(), quote.getArr(), quote.getTcv(), quote.getAcv(), quote.isRampScheduleStale(),
                new QuoteComparisonDto.Margin("UNAVAILABLE", null, "A verified cost basis is not available"), lines);
    }

    private UserContext user() {
        if (SecurityUtils.getCurrentCaller().isPresent() && SecurityUtils.getCurrentUser().isEmpty()) {
            throw new BusinessException("A verified seller is required", HttpStatus.FORBIDDEN,
                    "QUOTE_COMPARISON_USER_REQUIRED");
        }
        UserContext user = SecurityUtils.getCurrentUser().orElseThrow(() -> new BusinessException(
                "A verified seller with workforce context is required", HttpStatus.UNAUTHORIZED,
                "QUOTE_COMPARISON_IDENTITY_REQUIRED"));
        if (user.getTenantId() == null || user.getTenantId().isBlank() || user.getUserId() == null
                || user.getUserId().isBlank()) {
            throw new BusinessException("A verified seller with workforce context is required", HttpStatus.UNAUTHORIZED,
                    "QUOTE_COMPARISON_IDENTITY_REQUIRED");
        }
        return user;
    }
}
