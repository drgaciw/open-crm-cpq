package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricedPeriodView;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.ramp.*;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Fail-closed quote-owned boundary from persisted priced facts to the accepted v1 contract. */
final class AcceptedRampSnapshots {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> TYPES = Set.of("PRODUCT", "SERVICE", "SUBSCRIPTION", "SAAS_SEAT_NAMED",
            "SAAS_SEAT_CONCURRENT", "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
    private static final Set<String> FREQUENCIES = Set.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL");
    private static final Set<String> FIXED_PRODUCTS = Set.of("SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
    private AcceptedRampSnapshots() {}

    static boolean recurring(QuoteLineItem line) {
        return TYPES.contains(Objects.toString(line.getLineItemType(), ""))
                && (FIXED_PRODUCTS.contains(Objects.toString(line.getProductType(), ""))
                    || (line.getProductType() == null && line.getQuote() != null && line.getQuote().getTargetSubscriptionNumber() == null
                        && !RampScheduleViews.hasSteps(line) && line.getInitialProrationJson() == null))
                && FREQUENCIES.contains(Objects.toString(line.getBillingFrequency(), ""));
    }

    static HybridMigrationContext hybrid(QuoteLineItem line) {
        if (line.getHybridMigrationJson() == null) return null;
        try { return JSON.readValue(line.getHybridMigrationJson(), HybridMigrationContext.class); }
        catch (Exception ex) { throw invalid("Unreadable hybrid migration inputs"); }
    }

    static boolean matchesAuthority(QuoteLineItem line, Object authoritativeType) {
        return authoritativeType instanceof String type && FIXED_PRODUCTS.contains(type)
                && type.equals(line.getProductType());
    }

    private static boolean hasPersistedAuthority(QuoteLineItem line) {
        var proof = RampPricingSnapshot.read(line.getPricingWaterfallJson()).get("authoritativeProductType");
        return proof != null && proof.isTextual() && matchesAuthority(line, proof.textValue());
    }

    static QuoteAcceptedEvent.ProrationSnapshot proration(Quote quote, QuoteLineItem line) {
        if (line.getInitialProrationJson() == null) return null;
        try {
            var value = JSON.readValue(line.getInitialProrationJson(), QuoteAcceptedEvent.ProrationSnapshot.class);
            var start = java.time.LocalDate.parse(value.getStartDate());
            var end = java.time.LocalDate.parse(value.getEndDate());
            var billingStart = java.time.LocalDate.parse(value.getBillingPeriodStart());
            var billingEnd = java.time.LocalDate.parse(value.getBillingPeriodEnd());
            if (!hasPersistedAuthority(line) || quote.getTargetSubscriptionNumber() == null || quote.isRampScheduleStale()
                    || !Objects.equals(quote.getSubscriptionEffectiveDate(), start) || start.isBefore(billingStart)
                    || end.isBefore(start) || !end.equals(billingEnd) || end.isAfter(quote.getEndDate())
                    || value.getQuantity() == null || value.getQuantity() != line.getQuantity() || value.getQuantity() < 1
                    || negativeOrNull(value.getNetUnitPrice()) || negativeOrNull(value.getNetMonthly()) || negativeOrNull(value.getPeriodAmount()))
                throw invalid("Invalid typed initial proration");
            BigDecimal monthly = money(value.getNetUnitPrice().multiply(BigDecimal.valueOf(value.getQuantity())));
            long remaining = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            long full = java.time.temporal.ChronoUnit.DAYS.between(billingStart, billingEnd) + 1;
            if (full < 1 || monthly.compareTo(value.getNetMonthly()) != 0
                    || monthly.multiply(BigDecimal.valueOf(remaining)).divide(BigDecimal.valueOf(full), 2, RoundingMode.HALF_UP)
                    .compareTo(value.getPeriodAmount()) != 0
                    || (RampScheduleViews.hasSteps(line) ? !end.plusDays(1).equals(quote.getStartDate())
                    : (!start.equals(quote.getStartDate()) || !end.equals(quote.getEndDate()) || quote.getDurationInMonths() != 1)))
                throw invalid("Initial proration does not match the actual billing interval");
            return value;
        }
        catch (Exception ex) { throw invalid("Unreadable initial proration"); }
    }

    static List<QuoteAcceptedEvent.RampPeriodSnapshot> periods(Quote quote, QuoteLineItem line) {
        boolean scheduled = RampScheduleViews.hasSteps(line);
        if (scheduled && quote.isRampScheduleStale()) throw invalid("Ramp prices are stale; recalculate");
        if (!recurring(line)) {
            if (scheduled || (quote.getTargetSubscriptionNumber() != null
                    && TYPES.contains(Objects.toString(line.getLineItemType(), ""))
                    && !"ONE_TIME".equals(line.getBillingFrequency())
                    && !Set.of("ONPREM_PERPETUAL", "PERPETUAL").contains(Objects.toString(line.getProductType(), ""))))
                throw invalid("Unsupported recurring rate facts");
            return null;
        }
        if (!hasPersistedAuthority(line)) {
            if (scheduled || quote.getTargetSubscriptionNumber() != null)
                throw invalid("Reusable recurring rates require matching authoritative pricing product classification");
            return null; // Legacy NEW scalar pricing does not establish reusable fixed rate authority.
        }
        List<PricedPeriodView> priced = null;
        if (scheduled) {
            try {
                priced = JSON.convertValue(RampPricingSnapshot.read(line.getPricingWaterfallJson()).get("pricedPeriods"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<PricedPeriodView>>() {});
            } catch (IllegalArgumentException ex) { throw invalid("Malformed persisted priced ramp periods"); }
        }
        if (scheduled && (priced == null || priced.isEmpty())) throw invalid("Persisted ramp prices are missing");
        if (!scheduled) {
            if (line.getProductType() == null) return null; // Scalar legacy NEW facts do not establish reusable rate classification.
            if (proration(quote, line) != null) return null; // Explicit dated interval, not a made-up monthly ramp.
            BigDecimal unit = line.getNetPrice();
            if (unit == null || line.getQuantity() <= 0 || line.getExtendedAmount() == null || quote.getDurationInMonths() < 1
                    || unit.signum() < 0 || money(unit.multiply(BigDecimal.valueOf(line.getQuantity()))).compareTo(line.getExtendedAmount()) != 0) {
                if (quote.getTargetSubscriptionNumber() != null) throw invalid("A complete priced unit rate is required for this subscription change");
                return null; // Historical aggregate-only accepted events remain readable.
            }
            priced = List.of(PricedPeriodView.builder().sequence(1).startMonth(1).months(quote.getDurationInMonths())
                    .quantity(line.getQuantity()).netUnitPrice(unit).netMonthly(line.getExtendedAmount())
                    .periodAmount(money(line.getExtendedAmount().multiply(BigDecimal.valueOf(quote.getDurationInMonths()))))
                    .discountRate(line.getDiscountRate()).build());
        }
        List<QuoteAcceptedEvent.RampPeriodSnapshot> result = new ArrayList<>();
        for (PricedPeriodView candidate : priced) {
            if (candidate == null || candidate.getMonths() == null || candidate.getMonths() < 1
                    || negativeOrNull(candidate.getNetMonthly())) throw invalid("Malformed persisted priced ramp period");
        }
        int nextMonth = 1;
        for (int index = 0; index < priced.size(); index++) {
            PricedPeriodView period = priced.get(index);
            if (period == null || !Objects.equals(period.getSequence(), index + 1) || !Objects.equals(period.getStartMonth(), nextMonth)
                    || period.getMonths() == null || period.getMonths() < 1 || period.getQuantity() == null || period.getQuantity() < 0
                    || negativeOrNull(period.getNetUnitPrice()) || negativeOrNull(period.getNetMonthly()) || negativeOrNull(period.getPeriodAmount())
                    || (period.getDiscountRate() != null && (period.getDiscountRate().signum() < 0 || period.getDiscountRate().compareTo(BigDecimal.ONE) > 0))) {
                throw invalid("Malformed persisted priced ramp period");
            }
            BigDecimal monthly = money(period.getNetUnitPrice().multiply(BigDecimal.valueOf(period.getQuantity())));
            BigDecimal gross = money(monthly.multiply(BigDecimal.valueOf(period.getMonths())));
            if (monthly.compareTo(period.getNetMonthly()) != 0) throw invalid("Persisted monthly rate does not match quantity and unit price");
            if (period.getMonths() > quote.getDurationInMonths() - nextMonth + 1) throw invalid("Persisted ramp exceeds the quote term");
            boolean credit = period.getGrossPeriodAmount() != null || period.getMigrationCredit() != null;
            if (credit) {
                HybridMigrationContext context = hybrid(line);
                if (index != 0 || context == null || !"DECLINING".equals(context.role())
                        || negativeOrNull(period.getGrossPeriodAmount()) || negativeOrNull(period.getMigrationCredit())
                        || gross.compareTo(period.getGrossPeriodAmount()) != 0
                        || period.getMigrationCredit().compareTo(gross) > 0
                        || period.getPeriodAmount().compareTo(gross.subtract(period.getMigrationCredit())) != 0) {
                    throw invalid("Unauthorized or inconsistent migration credit");
                }
                BigDecimal lineGross = priced.stream().map(p -> money(p.getNetMonthly().multiply(BigDecimal.valueOf(p.getMonths()))))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal expected = money(context.bookValue().multiply(new BigDecimal("0.30"))).min(lineGross).min(gross);
                if (expected.compareTo(period.getMigrationCredit()) != 0) throw invalid("Migration credit does not match the typed book value");
            } else if (gross.compareTo(period.getPeriodAmount()) != 0) throw invalid("Persisted amount does not match the monthly rate and term");
            nextMonth = Math.addExact(nextMonth, period.getMonths());
            result.add(QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(period.getSequence()).startMonth(period.getStartMonth())
                    .months(period.getMonths()).quantity(period.getQuantity()).netUnitPrice(period.getNetUnitPrice())
                    .netMonthly(period.getNetMonthly()).periodAmount(period.getPeriodAmount()).discountRate(period.getDiscountRate())
                    .explanationCodes(period.getExplanationCodes()).grossPeriodAmount(period.getGrossPeriodAmount())
                    .migrationCredit(period.getMigrationCredit()).build());
        }
        if (nextMonth - 1 != quote.getDurationInMonths()) throw invalid("Persisted ramp does not cover the quote term");
        return List.copyOf(result);
    }

    static void validatePairs(Quote quote, List<QuoteLineItem> lines) {
        Map<String, List<QuoteLineItem>> pairs = new HashMap<>();
        lines.stream().filter(l -> hybrid(l) != null).forEach(l -> pairs.computeIfAbsent(hybrid(l).pairId(), ignored -> new ArrayList<>()).add(l));
        for (List<QuoteLineItem> pair : pairs.values()) {
            if (pair.size() != 2) throw invalid("Hybrid migration requires exactly one declining and one rising line");
            QuoteLineItem declining = pair.stream().filter(l -> "DECLINING".equals(hybrid(l).role())).findFirst().orElseThrow(() -> invalid("Missing declining line"));
            QuoteLineItem rising = pair.stream().filter(l -> "RISING".equals(hybrid(l).role())).findFirst().orElseThrow(() -> invalid("Missing rising line"));
            HybridMigrationContext d = hybrid(declining), r = hybrid(rising);
            if (!Objects.equals(d.startMonth(), r.startMonth()) || !Objects.equals(d.months(), r.months())
                    || declining.getProductExternalId() == null || declining.getProductExternalId().isBlank()
                    || rising.getProductExternalId() == null || rising.getProductExternalId().isBlank()
                    || declining.getProductExternalId().equals(rising.getProductExternalId())
                    || (long)d.startMonth() + d.months() - 1 > quote.getDurationInMonths()
                    || !"ONPREM_SUPPORT".equals(declining.getProductType()) || !"HYBRID_MIGRATION_RAMP".equals(rising.getProductType())
                    || !"ONPREM_SUPPORT".equals(declining.getLineItemType()) || !"HYBRID_MIGRATION_RAMP".equals(rising.getLineItemType()))
                throw invalid("Hybrid migration product roles or windows disagree");
            List<QuoteAcceptedEvent.RampPeriodSnapshot> dp = periods(quote, declining), rp = periods(quote, rising);
            if (dp == null || rp == null || dp.getFirst().getMigrationCredit() == null) throw invalid("Missing validated migration priced periods");
            for (int index = 1; index < dp.size(); index++) {
                if (dp.get(index).getQuantity() > dp.get(index - 1).getQuantity())
                    throw invalid("Declining hybrid migration quantity must not increase");
            }
            for (int index = 1; index < rp.size(); index++) {
                if (rp.get(index).getQuantity() < rp.get(index - 1).getQuantity())
                    throw invalid("Rising hybrid migration quantity must not decrease");
            }
            int length = quote.getDurationInMonths();
            for (int month = 1; month <= length; month++) {
                final int current = month;
                boolean overlap = dp.stream().anyMatch(p -> current >= p.getStartMonth() && current < p.getStartMonth() + p.getMonths() && p.getQuantity() > 0)
                        && rp.stream().anyMatch(p -> current >= p.getStartMonth() && current < p.getStartMonth() + p.getMonths() && p.getQuantity() > 0);
                if (overlap != (month >= d.startMonth() && month < (long)d.startMonth() + d.months())) throw invalid("Hybrid migration overlap differs from the validated window");
            }
        }
    }

    private static boolean negativeOrNull(BigDecimal value) { return value == null || value.signum() < 0; }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BusinessException invalid(String message) { return new BusinessException(message, HttpStatus.CONFLICT, "ACCEPTED_RAMP_SNAPSHOT_INVALID"); }
}
