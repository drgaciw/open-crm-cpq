package com.opencrm.quote.comparison;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.ramp.QuoteRampStep;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComparisonService {
    private final QuoteRepository repository;
    private final QuoteMapper mapper;
    private final EntityManager entityManager;

    @Transactional
    public QuoteDto markPrimary(Long id) {
        String tenant = tenant();
        Quote target = repository.findByIdAndTenantIdAndDeletedFalse(id, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        String deal = opportunity(target.getDealExternalId());
        // Serialize even when no primary exists; row locks alone cannot protect that empty set.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .setParameter("scope", tenant.length() + ":" + tenant + deal).getSingleResult();
        entityManager.refresh(target, LockModeType.PESSIMISTIC_WRITE);
        if (target.isDeleted() || !tenant.equals(target.getTenantId()) || !deal.equals(target.getDealExternalId())) {
            throw new BusinessException("Quote changed; refresh and retry", HttpStatus.CONFLICT, "QUOTE_CHANGED");
        }
        List<Quote> alternatives = repository.findByTenantIdAndDealExternalIdAndDeletedFalseOrderByIdAsc(tenant, deal);
        alternatives.stream().filter(q -> !q.getId().equals(id) && q.isPrimary()).forEach(q -> q.setPrimary(false));
        repository.flush(); // Partial unique indexes are immediate, so clear before setting the replacement.
        target.setPrimary(true);
        repository.flush();
        return mapper.toDto(target);
    }

    @Transactional(readOnly = true)
    public QuoteComparisonDto compare(String dealExternalId, List<Long> quoteIds) {
        String tenant = tenant();
        String deal = opportunity(dealExternalId);
        List<Quote> all = repository.findByTenantIdAndDealExternalIdAndDeletedFalseOrderByIdAsc(tenant, deal);
        List<Quote> selected = all;
        if (quoteIds != null && !quoteIds.isEmpty()) {
            Set<Long> ids = new HashSet<>(quoteIds);
            selected = all.stream().filter(q -> ids.contains(q.getId())).toList();
            if (selected.size() != ids.size()) {
                throw new ResourceNotFoundException("One or more quotes do not belong to this opportunity");
            }
        }
        // Stable id baseline avoids nonsensical lowest-price ordering across currencies.
        Quote baseline = selected.stream().filter(Quote::isPrimary).findFirst()
                .orElse(selected.isEmpty() ? null : selected.getFirst());
        Quote base = baseline;
        return new QuoteComparisonDto(deal, base == null ? null : base.getId(),
                selected.stream().map(q -> column(q, base)).toList(),
                List.of("marginAmount", "marginPercent", "validUntil", "paymentTerms"));
    }

    private static String tenant() {
        String tenant = QuoteTenantResolver.currentTenantId();
        if (tenant == null) throw new BusinessException("Tenant identity is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        return tenant;
    }

    private static String opportunity(String deal) {
        if (deal == null || deal.isBlank()) throw new BusinessException("An opportunity is required", "OPPORTUNITY_REQUIRED");
        return deal;
    }

    private static QuoteComparisonDto.Column column(Quote q, Quote base) {
        List<QuoteLineItem> lines = lines(q);
        BigDecimal listTotal = lines.stream().map(l -> zero(l.getListPrice()).multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean sameCurrency = q.getCurrency() != null && q.getCurrency().equals(base.getCurrency());
        BigDecimal totalDelta = sameCurrency ? zero(q.getTotalAmount()).subtract(zero(base.getTotalAmount())) : null;
        var delta = new QuoteComparisonDto.Delta(sameCurrency,
                sameCurrency ? zero(q.getSubtotal()).subtract(zero(base.getSubtotal())) : null,
                sameCurrency ? zero(q.getTotalDiscount()).subtract(zero(base.getTotalDiscount())) : null,
                sameCurrency ? zero(q.getTotalTax()).subtract(zero(base.getTotalTax())) : null,
                totalDelta, sameCurrency ? percent(totalDelta, zero(base.getTotalAmount())) : null);
        return new QuoteComparisonDto.Column(q.getId(), q.getQuoteNumber(), q.getName(), q.isPrimary(), q.getStatus(),
                q.getCurrency(), zero(q.getSubtotal()), listTotal, zero(q.getTotalDiscount()),
                percent(zero(q.getTotalDiscount()), zero(q.getSubtotal())), zero(q.getTotalTax()), zero(q.getTotalAmount()),
                zero(q.getSubtotal()).subtract(zero(q.getTotalDiscount())), null, null,
                lines.stream().map(QuoteLineItem::getBillingFrequency).filter(Objects::nonNull).distinct().sorted().toList(),
                q.getDurationInMonths(), null, null, lines.size(), delta, diff(q, base));
    }

    private static List<QuoteLineItem> lines(Quote q) {
        return q.getLineItems().stream().filter(l -> !l.isDeleted()).toList();
    }

    private static QuoteComparisonDto.SkuDiff diff(Quote q, Quote base) {
        Map<String, List<String>> current = skus(q), baseline = skus(base);
        return new QuoteComparisonDto.SkuDiff(
                current.keySet().stream().filter(k -> !baseline.containsKey(k)).sorted().toList(),
                baseline.keySet().stream().filter(k -> !current.containsKey(k)).sorted().toList(),
                current.keySet().stream().filter(baseline::containsKey)
                        .filter(k -> !current.get(k).equals(baseline.get(k))).sorted().toList());
    }

    private static Map<String, List<String>> skus(Quote q) {
        return lines(q).stream().collect(Collectors.groupingBy(QuoteLineItem::getProductExternalId,
                Collectors.mapping(l -> l.getQuantity() + ":" + zero(l.getListPrice()).stripTrailingZeros().toPlainString()
                        + ":" + zero(l.getUnitPrice()).stripTrailingZeros().toPlainString()
                        + ":" + zero(l.getDiscountRate()).stripTrailingZeros().toPlainString()
                        + ":" + Objects.toString(l.getBillingFrequency(), "")
                        + ":" + rampSignature(l),
                        Collectors.collectingAndThen(Collectors.toList(), values -> values.stream().sorted().toList()))));
    }

    private static List<String> rampSignature(QuoteLineItem line) {
        return line.getRampSteps().stream()
                .filter(step -> !step.isDeleted())
                .sorted(Comparator.comparingInt(QuoteRampStep::getSequence))
                .map(step -> step.getStartMonth() + ":" + step.getMonths() + ":" + step.getQuantity()
                        + ":" + step.getPriceBasis() + ":" + decimalSignature(step.getUnitPrice())
                        + ":" + decimalSignature(step.getDiscountFraction()))
                .toList();
    }

    private static String decimalSignature(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal percent(BigDecimal amount, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : amount.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
