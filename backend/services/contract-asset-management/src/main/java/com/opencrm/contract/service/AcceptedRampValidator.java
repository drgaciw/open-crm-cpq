package com.opencrm.contract.service;

import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.event.dto.QuoteAcceptedEvent.LineItemSnapshot;
import com.opencrm.common.event.dto.QuoteAcceptedEvent.RampPeriodSnapshot;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/** Validates accepted facts locally; it never calls or shares a validator with pricing. */
@Component
public class AcceptedRampValidator {
    private static final Set<String> BILLING = Set.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL");
    private static final Set<String> FIXED_RECURRING = Set.of("PRODUCT", "SERVICE", "SUBSCRIPTION",
            "SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
    private static final Set<String> FIXED_PRODUCTS = Set.of("SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT",
            "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public record Validated(LocalDate startDate, LocalDate endDate, List<SubscriptionRatePeriod> periods,
                            BigDecimal firstMrr, BigDecimal exitMrr, String planName) {}

    public boolean hasTypedRates(QuoteAcceptedEvent event) {
        return event.getLineItems() != null && event.getLineItems().stream()
                .anyMatch(line -> line != null && (line.getRamp() != null || line.getInitialProration() != null));
    }

    public Validated validate(QuoteAcceptedEvent event, LocalDate anchor, int anchorOffset) {
        return validateMutation(event, anchor, anchorOffset, null, null);
    }

    public Validated validateMutation(QuoteAcceptedEvent event, LocalDate anchor, int anchorOffset,
                                     LocalDate effectiveDate, LocalDate targetEnd) {
        require(event.getTerms() != null, "Typed rates require accepted terms");
        LocalDate start = date(event.getTerms().getStartDate(), "terms.startDate");
        LocalDate end = date(event.getTerms().getEndDate(), "terms.endDate");
        Integer term = event.getTerms().getTermMonths();
        require(term != null && term > 0 && !end.isBefore(start), "Invalid accepted term");
        require(event.getLineItems() != null && !event.getLineItems().isEmpty(), "Typed rates require lines");
        for (var line : event.getLineItems()) {
            require(line != null, "Null accepted line");
            if (line.getRamp() != null || line.getInitialProration() != null) {
                require(recurring(line), "Typed rate schedule has unsupported product classification or line kind");
            }
        }
        LocalDate calendarAnchor = anchor == null ? start : anchor;
        boolean anyPartial = event.getLineItems() != null && event.getLineItems().stream().anyMatch(line -> line != null && line.getInitialProration() != null);
        boolean partialOnly = anyPartial && event.getLineItems().stream().filter(java.util.Objects::nonNull).filter(AcceptedRampValidator::recurring)
                .allMatch(line -> line.getRamp() == null || line.getRamp().isEmpty());
        require(!anyPartial || (anchor != null && effectiveDate != null && targetEnd != null
                && event.getSubscriptionChange() != null && event.getSubscriptionChange().getEffectiveDate() != null),
                "Initial proration requires explicit verified target context");
        require(partialOnly ? term == 1 && start.equals(effectiveDate) && end.equals(targetEnd)
                : calendarAnchor.plusMonths(anchorOffset).equals(start)
                && calendarAnchor.plusMonths((long) anchorOffset + term).minusDays(1).equals(end),
                "Accepted term does not match the anchored month calendar");
        require(event.getCurrencyCode() != null && event.getCurrencyCode().matches("[A-Z]{3}"), "Invalid currency");
        require(event.getLineItems() != null && !event.getLineItems().isEmpty(), "Typed rates require lines");
        List<SubscriptionRatePeriod> rates = new ArrayList<>();
        List<String> products = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        BigDecimal firstMrr = BigDecimal.ZERO;
        BigDecimal exitMrr = BigDecimal.ZERO;
        BigDecimal flatPublishedMonthly = BigDecimal.ZERO;
        BigDecimal publishedTcv = BigDecimal.ZERO;
        for (LineItemSnapshot line : event.getLineItems()) {
            require(line != null, "Null accepted line");
            boolean recurring = recurring(line);
            if (!recurring) {
                require(line.getRamp() == null && line.getInitialProration() == null,
                        "Typed rate schedule has unsupported product classification or line kind");
                require(line.getExtendedAmount() != null, "Flat accepted line lacks published amount");
                // Existing quote metrics treat all flat final prices as monthly. Preserve that wire meaning,
                // while these unsupported/nonrecurring lines never become subscription rate rows.
                flatPublishedMonthly = flatPublishedMonthly.add(line.getExtendedAmount());
                publishedTcv = publishedTcv.add(line.getExtendedAmount().multiply(BigDecimal.valueOf(term)));
                continue;
            }
            require(partialOnly || (line.getRamp() != null && !line.getRamp().isEmpty()), "Recurring line lacks accepted priced rates");
            require(!anyPartial || line.getInitialProration() != null, "Every recurring line must cover the initial partial interval");
            require(uuid(line.getLineUuid()) && identities.add(line.getLineUuid()), "Invalid or duplicate accepted line UUID");
            String productLabel = line.getProductName();
            if (productLabel == null || productLabel.isBlank()) productLabel = line.getProductExternalId();
            require(productLabel != null && !productLabel.isBlank(), "Accepted product name or identifier is missing");
            products.add(productLabel);
            long nextMonth = 1;
            int sequence = 1;
            BigDecimal lineAmount = BigDecimal.ZERO;
            SubscriptionRatePeriod partial = line.getInitialProration() == null ? null
                    : proration(event, line, calendarAnchor, effectiveDate, targetEnd, start, partialOnly);
            if (partial != null) {
                rates.add(partial);
                lineAmount = partial.getPeriodAmount();
                firstMrr = firstMrr.add(partial.getNetMonthly());
                if (partialOnly) exitMrr = exitMrr.add(partial.getNetMonthly());
            }
            List<RampPeriodSnapshot> ordinary = line.getRamp() == null ? List.of() : line.getRamp();
            for (RampPeriodSnapshot period : ordinary) {
                require(period != null && period.getSequence() != null && period.getSequence() == sequence
                        && period.getStartMonth() != null && period.getStartMonth() == nextMonth
                        && period.getMonths() != null && period.getMonths() > 0
                        && period.getMonths() <= term && nextMonth <= term - period.getMonths() + 1, "Noncontiguous accepted periods");
                require(period.getQuantity() != null && period.getQuantity() >= 0, "Invalid period quantity");
                nonnegative(period.getNetUnitPrice(), "netUnitPrice");
                nonnegative(period.getNetMonthly(), "netMonthly");
                nonnegative(period.getPeriodAmount(), "periodAmount");
                equal(money(period.getNetUnitPrice().multiply(BigDecimal.valueOf(period.getQuantity()))),
                        period.getNetMonthly(), "Monthly rate differs from quantity times accepted unit price");
                BigDecimal gross = money(period.getNetMonthly().multiply(BigDecimal.valueOf(period.getMonths())));
                boolean credited = period.getMigrationCredit() != null || period.getGrossPeriodAmount() != null;
                if (credited) {
                    require(period.getGrossPeriodAmount() != null && period.getMigrationCredit() != null,
                            "Credited period requires both gross and credit");
                    nonnegative(period.getMigrationCredit(), "migrationCredit");
                    equal(gross, period.getGrossPeriodAmount(), "Invalid gross amount");
                    require(period.getMigrationCredit().compareTo(gross) <= 0, "Credit exceeds authorized gross amount");
                    require(sequence == 1 && line.getHybridMigration() != null
                            && "DECLINING".equals(line.getHybridMigration().getRole()), "Credit is not authorized for this period");
                    equal(gross.subtract(period.getMigrationCredit()), period.getPeriodAmount(), "Credited period net amount mismatch");
                } else {
                    equal(gross, period.getPeriodAmount(), "Period amount differs from accepted monthly rate times months");
                }
                if (period.getDiscountRate() != null) {
                    require(period.getDiscountRate().signum() >= 0 && period.getDiscountRate().compareTo(BigDecimal.ONE) <= 0,
                            "Invalid discount fraction");
                }
                LocalDate periodStart = calendarAnchor.plusMonths((long) anchorOffset + nextMonth - 1);
                LocalDate periodEnd = calendarAnchor.plusMonths((long) anchorOffset + nextMonth - 1 + period.getMonths()).minusDays(1);
                rates.add(SubscriptionRatePeriod.builder().acceptedLineUuid(line.getLineUuid())
                        .sourceLineUuid(line.getSourceSubscriptionLineUuid()).acceptedQuoteId(event.getQuoteId())
                        .productExternalId(line.getProductExternalId()).productName(line.getProductName())
                        .productType(line.getProductType()).lineItemType(line.getLineItemType()).billingFrequency(line.getBillingFrequency())
                        .sequence(sequence).startMonth(Math.toIntExact(nextMonth)).months(period.getMonths())
                        .startDate(periodStart).billingAnchorDate(calendarAnchor).endDate(periodEnd).effectiveEndDate(periodEnd)
                        .quantity(BigDecimal.valueOf(period.getQuantity())).netUnitPrice(period.getNetUnitPrice())
                        .netMonthly(period.getNetMonthly()).annualRunRate(money(period.getNetMonthly().multiply(TWELVE)))
                        .periodAmount(period.getPeriodAmount()).grossPeriodAmount(period.getGrossPeriodAmount())
                        .migrationCredit(period.getMigrationCredit()).discountRate(period.getDiscountRate())
                        .explanationCodes(period.getExplanationCodes()).currencyCode(event.getCurrencyCode())
                        .provenance("ACCEPTED_PRICED").build());
                if (sequence == 1 && partial == null) firstMrr = firstMrr.add(period.getNetMonthly());
                if (sequence == ordinary.size()) exitMrr = exitMrr.add(period.getNetMonthly());
                lineAmount = lineAmount.add(period.getPeriodAmount());
                nextMonth += period.getMonths();
                sequence++;
            }
            require(partialOnly ? ordinary.isEmpty() : (long) nextMonth == (long) term + 1, "Accepted rates do not cover the full term");
            equal(partial == null ? ordinary.getFirst().getNetMonthly() : partial.getNetMonthly(), line.getExtendedAmount(),
                    "Accepted line scalar differs from earliest monthly price");
            publishedTcv = publishedTcv.add(lineAmount);
        }
        require(!rates.isEmpty(), "No supported fixed recurring rates");
        validateMigrationPairs(event);
        require(event.getPricing() != null, "Typed rates require accepted pricing");
        equal(money(firstMrr.add(flatPublishedMonthly).multiply(TWELVE)), event.getPricing().getArr(), "Published ARR differs from accepted line facts");
        equal(money(exitMrr.add(flatPublishedMonthly).multiply(TWELVE)), event.getPricing().getExitArr(), "Published exit ARR differs from accepted line facts");
        equal(money(publishedTcv), event.getPricing().getTcv(), "Published TCV differs from accepted priced facts");
        equal(publishedTcv.multiply(TWELVE).divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP),
                event.getPricing().getAcv(), "Published ACV differs from accepted TCV and term");
        // Existing quote totals sum each line's earliest monthly finalPrice, independently of term TCV.
        BigDecimal scalarTotal = money(event.getLineItems().stream().map(LineItemSnapshot::getExtendedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        equal(scalarTotal, event.getTotalAmount(), "Accepted scalar total differs from accepted line prices");
        equal(scalarTotal, event.getPricing().getTotalAmount(), "Published pricing scalar total differs from accepted line prices");
        if (event.getPricing().getCurrencyCode() != null) require(event.getCurrencyCode().equals(event.getPricing().getCurrencyCode()), "Currency mismatch");
        return new Validated(start, end, List.copyOf(rates), money(firstMrr), money(exitMrr),
                String.join(", ", products.stream().distinct().toList()));
    }

    private SubscriptionRatePeriod proration(QuoteAcceptedEvent event, LineItemSnapshot line, LocalDate anchor,
            LocalDate effective, LocalDate targetEnd, LocalDate fullStart, boolean partialOnly) {
        var partial = line.getInitialProration();
        require(line.getHybridMigration() == null, "Initial proration does not authorize a new migration credit");
        long index = java.time.temporal.ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), effective.withDayOfMonth(1));
        if (index >= 0 && anchor.plusMonths(index).isAfter(effective)) index--;
        require(index >= 0, "Proration precedes its source billing anchor");
        LocalDate billingStart = anchor.plusMonths(index);
        LocalDate nextBoundary = anchor.plusMonths(index + 1);
        LocalDate billingEnd = nextBoundary.minusDays(1);
        LocalDate intervalEnd = billingEnd.isAfter(targetEnd) ? targetEnd : billingEnd;
        require(effective.isAfter(billingStart) && !effective.isAfter(intervalEnd), "Proration must be a proper partial billing interval");
        require(date(partial.getStartDate(), "proration.startDate").equals(effective)
                && date(partial.getEndDate(), "proration.endDate").equals(intervalEnd)
                && date(partial.getBillingPeriodStart(), "proration.billingPeriodStart").equals(billingStart)
                && date(partial.getBillingPeriodEnd(), "proration.billingPeriodEnd").equals(billingEnd), "Proration dates disagree with the original source billing anchor");
        require(partialOnly ? intervalEnd.equals(targetEnd) && nextBoundary.isAfter(targetEnd)
                : nextBoundary.equals(fullStart) && nextBoundary.isBefore(targetEnd.plusDays(1)), "Proration and following whole periods overlap or leave a gap");
        require(partial.getQuantity() != null && partial.getQuantity() >= 0, "Invalid partial quantity");
        nonnegative(partial.getNetUnitPrice(), "partial netUnitPrice");
        nonnegative(partial.getNetMonthly(), "partial netMonthly");
        nonnegative(partial.getPeriodAmount(), "partial periodAmount");
        equal(money(partial.getNetUnitPrice().multiply(BigDecimal.valueOf(partial.getQuantity()))), partial.getNetMonthly(), "Partial unit rate differs from monthly amount");
        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(effective, intervalEnd) + 1;
        long billingDays = java.time.temporal.ChronoUnit.DAYS.between(billingStart, billingEnd) + 1;
        BigDecimal amount = partial.getNetMonthly().multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(billingDays), 2, RoundingMode.HALF_UP);
        equal(amount, partial.getPeriodAmount(), "Partial amount differs from exact inclusive actual-day ratio");
        return SubscriptionRatePeriod.builder().acceptedLineUuid(line.getLineUuid()).sourceLineUuid(line.getSourceSubscriptionLineUuid())
                .acceptedQuoteId(event.getQuoteId()).productExternalId(line.getProductExternalId()).productName(line.getProductName())
                .productType(line.getProductType()).lineItemType(line.getLineItemType()).billingFrequency(line.getBillingFrequency())
                .sequence(0).startDate(effective).billingAnchorDate(anchor).endDate(intervalEnd).effectiveEndDate(intervalEnd)
                .quantity(BigDecimal.valueOf(partial.getQuantity())).netUnitPrice(partial.getNetUnitPrice()).netMonthly(partial.getNetMonthly())
                .annualRunRate(money(partial.getNetMonthly().multiply(TWELVE))).periodAmount(partial.getPeriodAmount())
                .currencyCode(event.getCurrencyCode()).provenance("ACCEPTED_PRORATED")
                .billingPeriodStart(billingStart).billingPeriodEnd(billingEnd).build();
    }

    private void validateMigrationPairs(QuoteAcceptedEvent event) {
        Map<String, List<LineItemSnapshot>> pairs = new HashMap<>();
        for (var line : event.getLineItems()) {
            var migration = line.getHybridMigration();
            if (migration == null) continue;
            require(recurring(line) && line.getRamp() != null && !line.getRamp().isEmpty(), "Migration requires fixed recurring priced rates");
            require(migration.getPairId() != null && !migration.getPairId().isBlank()
                    && migration.getStartMonth() != null && migration.getStartMonth() > 0
                    && migration.getMonths() != null && migration.getMonths() > 0 && migration.getMonths() <= 12,
                    "Invalid authorized migration window");
            require(List.of("DECLINING", "RISING").contains(migration.getRole()), "Invalid authorized migration role");
            pairs.computeIfAbsent(migration.getPairId(), ignored -> new ArrayList<>()).add(line);
        }
        for (var members : pairs.values()) {
            require(members.size() == 2, "Migration requires exactly one declining and rising member");
            var declining = members.stream().filter(line -> "DECLINING".equals(line.getHybridMigration().getRole())).findFirst().orElse(null);
            var rising = members.stream().filter(line -> "RISING".equals(line.getHybridMigration().getRole())).findFirst().orElse(null);
            require(declining != null && rising != null, "Migration pair roles are not unique");
            require(declining.getProductExternalId() != null && !declining.getProductExternalId().isBlank()
                    && rising.getProductExternalId() != null && !rising.getProductExternalId().isBlank()
                    && !declining.getProductExternalId().trim().equals(rising.getProductExternalId().trim()),
                    "Migration pair requires distinct nonblank product identities");
            require("ONPREM_SUPPORT".equals(normalize(declining.getProductType()))
                    && "HYBRID_MIGRATION_RAMP".equals(normalize(rising.getProductType())), "Migration role has unsupported product authority");
            var window = declining.getHybridMigration();
            require(window.getStartMonth().equals(rising.getHybridMigration().getStartMonth())
                    && window.getMonths().equals(rising.getHybridMigration().getMonths()), "Migration pair window mismatch");
            require(rising.getHybridMigration().getBookValue() == null, "Rising member cannot authorize book credit");
            nonnegative(window.getBookValue(), "authorized migration bookValue");
            validateOverlap(positiveIntervals(declining, false), positiveIntervals(rising, true),
                    window.getStartMonth(), window.getMonths());
            BigDecimal grossTotal = declining.getRamp().stream().map(period -> money(period.getNetMonthly()
                    .multiply(BigDecimal.valueOf(period.getMonths())))).reduce(BigDecimal.ZERO, BigDecimal::add);
            var first = declining.getRamp().getFirst();
            require(first.getGrossPeriodAmount() != null && first.getMigrationCredit() != null,
                    "Declining first period lacks authorized gross and credit facts");
            BigDecimal authorized = money(window.getBookValue().multiply(new BigDecimal("0.30")))
                    .min(grossTotal).min(first.getGrossPeriodAmount());
            equal(authorized, first.getMigrationCredit(), "Migration credit differs from exact authorized thirty percent and caps");
        }
    }

    private List<long[]> positiveIntervals(LineItemSnapshot line, boolean increasing) {
        List<long[]> positive = new ArrayList<>();
        Integer previous = null;
        for (var period : line.getRamp()) {
            if (previous != null) require(increasing ? period.getQuantity() >= previous : period.getQuantity() <= previous,
                    "Migration quantity direction disagrees with authorized role");
            previous = period.getQuantity();
            if (period.getQuantity() > 0) positive.add(new long[]{period.getStartMonth(), (long) period.getStartMonth() + period.getMonths()});
        }
        return positive;
    }

    private void validateOverlap(List<long[]> declining, List<long[]> rising, int startMonth, int months) {
        int left = 0;
        int right = 0;
        long covered = 0;
        while (left < declining.size() && right < rising.size()) {
            long[] a = declining.get(left);
            long[] b = rising.get(right);
            long start = Math.max(a[0], b[0]);
            long end = Math.min(a[1], b[1]);
            if (end > start) {
                covered += end - start;
                require(start >= startMonth && end <= (long) startMonth + months && covered <= months,
                        "Migration positive overlap differs from the authorized window");
            }
            if (a[1] <= b[1]) left++;
            else right++;
        }
        require(covered == months, "Migration window differs from actual positive quantity overlap");
    }

    public static boolean recurring(LineItemSnapshot line) {
        String kind = normalize(line.getLineItemType());
        if (kind.isEmpty()) kind = normalize(line.getProductType());
        String product = normalize(line.getProductType());
        return BILLING.contains(normalize(line.getBillingFrequency())) && FIXED_RECURRING.contains(kind)
                && FIXED_PRODUCTS.contains(product);
    }
    static boolean fixedProduct(String value) { return FIXED_PRODUCTS.contains(normalize(value)); }
    static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    static void equal(BigDecimal expected, BigDecimal actual, String message) {
        require(actual != null && expected.compareTo(actual) == 0, message);
    }
    static void nonnegative(BigDecimal value, String field) { require(value != null && value.signum() >= 0, "Invalid " + field); }
    static void require(boolean valid, String message) { if (!valid) throw new NonRetryableEventException(message); }
    static LocalDate date(String value, String field) {
        try { return LocalDate.parse(value); } catch (RuntimeException e) { throw new NonRetryableEventException("Invalid " + field, e); }
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean uuid(String value) {
        try { return value != null && UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException e) { return false; }
    }
}
