package com.opencrm.pricing.ramp.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.pricing.RampStep;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.*;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Parses customer facts once, validates authoritative products/entitlement, then adjusts fresh priced output. */
public final class HybridMigrationPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> BILLING = Set.of("MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL");
    private static final Set<String> FIELDS = Set.of("pairId", "role", "startMonth", "months", "bookValue");
    private final int maxWindowMonths;
    private final MigrationCreditPolicy creditPolicy = new MigrationCreditPolicy();

    public HybridMigrationPolicy(int maxWindowMonths) {
        if (maxWindowMonths < 1 || maxWindowMonths > 12)
            throw new IllegalArgumentException("Migration window must be between 1 and 12 months");
        this.maxWindowMonths = maxWindowMonths;
    }

    public Plan prepare(PricingContext context, Map<String, PricingProduct> products, ResolveBatchResponse resolution) {
        Plan plan = prepare(context, products);
        for (Pair pair : plan.pairs) for (Member member : List.of(pair.declining, pair.rising)) {
            if (context.getLineItems().get(member.index).getListPriceOverride() != null) continue;
            var entry = resolution.getEntries() == null ? null : resolution.getEntries().get(member.productId);
            if (entry != null && entry.isEntryMatched() && entry.getListPrice() != null
                    && (!member.currency.equals(entry.getCurrency()) || !member.currency.equals(resolution.getPriceBookCurrency()))) {
                throw invalid("Resolved migration price currency must match authoritative product and quote currency");
            }
        }
        return plan;
    }

    public Plan prepare(PricingContext context, Map<String, PricingProduct> products) {
        Map<String, List<Member>> groups = new LinkedHashMap<>();
        for (int index = 0; index < context.getLineItems().size(); index++) {
            PricingLineRequest line = context.getLineItems().get(index);
            if (line.getMetadata() == null || !line.getMetadata().containsKey("hybridMigration")) continue;
            Object raw = line.getMetadata().get("hybridMigration");
            if (!(raw instanceof Map<?, ?> metadata) || !FIELDS.containsAll(metadata.keySet())) {
                throw invalid("hybridMigration must contain only typed migration fields");
            }
            String pairId = text(metadata.get("pairId"), "pairId");
            String role = text(metadata.get("role"), "role");
            if (!role.equals("DECLINING") && !role.equals("RISING")) throw invalid("Invalid migration role");
            int start = positiveInteger(metadata.get("startMonth"), "startMonth");
            int months = positiveInteger(metadata.get("months"), "months");
            long end = (long) start + months;
            if (months > maxWindowMonths || context.getDurationInMonths() == null
                    || end > (long) context.getDurationInMonths() + 1) throw invalid("Migration window exceeds term or policy");
            BigDecimal book = null;
            if (role.equals("DECLINING")) {
                book = number(metadata.get("bookValue"), "bookValue");
                if (book.signum() < 0) throw invalid("Negative migration book value");
            } else if (metadata.containsKey("bookValue")) {
                throw invalid("Book value belongs only to the declining member");
            }
            PricingProduct product = products.get(line.getProductExternalId());
            ProductType required = role.equals("DECLINING") ? ProductType.ONPREM_SUPPORT : ProductType.HYBRID_MIGRATION_RAMP;
            if (product == null || product.getProductType() != required
                    || !Objects.equals(product.getExternalId(), line.getProductExternalId())
                    || !required.name().equals(line.getLineItemType())) {
                throw invalid("Migration role does not match authoritative recurring product");
            }
            if (context.getCurrency() == null || !context.getCurrency().equals(product.getCurrency())) {
                throw invalid("Migration products must match quote currency");
            }
            if (line.getBillingFrequency() == null || !BILLING.contains(line.getBillingFrequency().toUpperCase(Locale.ROOT))
                    || line.getSchedule() == null || line.getSchedule().isEmpty()) {
                throw invalid("Migration members require explicit recurring schedules");
            }
            Member member = new Member(index, product.getExternalId(), product.getProductType(), product.getCurrency(),
                    pairId, role, start, months, book, entitlement(line.getSchedule(), context.getDurationInMonths(), role));
            groups.computeIfAbsent(pairId, ignored -> new ArrayList<>()).add(member);
        }
        List<Pair> pairs = new ArrayList<>();
        for (List<Member> members : groups.values()) {
            if (members.size() != 2) throw invalid("Migration pair requires exactly two members");
            Member declining = members.stream().filter(member -> member.role.equals("DECLINING")).findFirst().orElse(null);
            Member rising = members.stream().filter(member -> member.role.equals("RISING")).findFirst().orElse(null);
            if (declining == null || rising == null || declining.productId.equals(rising.productId)
                    || declining.start != rising.start || declining.months != rising.months) {
                throw invalid("Migration pair identities, roles and windows must agree");
            }
            List<Interval> overlap = new ArrayList<>();
            for (Interval left : declining.entitlement) for (Interval right : rising.entitlement) {
                int start = Math.max(left.start, right.start), end = Math.min(left.end, right.end);
                if (start < end) overlap.add(new Interval(start, end));
            }
            overlap.sort(Comparator.comparingInt(Interval::start));
            List<Interval> merged = new ArrayList<>();
            for (Interval interval : overlap) {
                if (!merged.isEmpty() && interval.start <= merged.getLast().end) {
                    Interval previous = merged.removeLast();
                    merged.add(new Interval(previous.start, Math.max(previous.end, interval.end)));
                } else merged.add(interval);
            }
            if (merged.size() != 1 || merged.getFirst().start != declining.start
                    || merged.getFirst().end != declining.start + declining.months) {
                throw invalid("Declared migration window must equal the actual contiguous entitlement overlap");
            }
            pairs.add(new Pair(declining, rising));
        }
        return new Plan(List.copyOf(pairs), maxWindowMonths);
    }

    public void apply(Plan plan, List<PricingLineResult> lines) {
        List<Adjustment> adjustments = new ArrayList<>();
        for (Pair pair : plan.pairs) {
            for (Member member : List.of(pair.declining, pair.rising)) {
                PricingLineResult line = lines.get(member.index);
                if (!member.productId.equals(line.getProductExternalId())) throw invalid("Priced migration identity changed");
            }
            PricingLineResult declining = lines.get(pair.declining.index);
            List<PricedPeriod> periods = declining.getPricedPeriods();
            if (periods == null || periods.isEmpty()) throw invalid("Migration priced periods missing");
            BigDecimal grossTcv = BigDecimal.ZERO;
            for (PricedPeriod period : periods) {
                if (period.getGrossPeriodAmount() != null || period.getMigrationCredit() != null) {
                    throw invalid("Migration credit already applied");
                }
                BigDecimal gross = period.getNetMonthly().multiply(BigDecimal.valueOf(period.getMonths())).setScale(2, RoundingMode.HALF_UP);
                if (period.getPeriodAmount() == null || gross.compareTo(period.getPeriodAmount()) != 0) {
                    throw invalid("Migration gross priced amount is inconsistent");
                }
                grossTcv = grossTcv.add(gross);
            }
            PricedPeriod first = periods.getFirst();
            BigDecimal firstGross = first.getPeriodAmount();
            MigrationCreditPolicy.Credit credit = creditPolicy.calculate(pair.declining.book, grossTcv, firstGross);
            adjustments.add(new Adjustment(pair, first, grossTcv, credit));
        }
        // All pairs must be fresh and internally consistent before any priced output or audit changes.
        for (Adjustment adjustment : adjustments) {
            Pair pair = adjustment.pair;
            for (Member member : List.of(pair.declining, pair.rising)) {
                PricingLineResult line = lines.get(member.index);
                Map<String, Object> metadata = new LinkedHashMap<>(line.getMetadata() == null ? Map.of() : line.getMetadata());
                metadata.remove("migrationCreditAudit");
                metadata.put("hybridMigration", member.input());
                line.setMetadata(metadata);
            }
            PricingLineResult declining = lines.get(pair.declining.index);
            PricedPeriod first = adjustment.first;
            BigDecimal firstGross = first.getPeriodAmount();
            MigrationCreditPolicy.Credit credit = adjustment.credit;
            first.setGrossPeriodAmount(firstGross);
            first.setMigrationCredit(credit.effectiveCredit());
            first.setPeriodAmount(credit.adjustedPeriodAmount());
            List<String> codes = new ArrayList<>(first.getExplanationCodes() == null ? List.of() : first.getExplanationCodes());
            codes.add("RAMP_MIGRATION_CREDIT");
            first.setExplanationCodes(codes);
            List<PricingWaterfallStep> steps = new ArrayList<>(first.getWaterfall() == null ? List.of() : first.getWaterfall());
            steps.add(PricingWaterfallStep.builder().stepType(WaterfallStepType.DISCOUNT)
                    .description("One-time migration credit on period amount")
                    .amount(credit.effectiveCredit().negate()).runningTotal(credit.adjustedPeriodAmount())
                    .ruleId("hybrid-migration-credit").build());
            first.setWaterfall(steps);
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("bookValue", pair.declining.book);
            audit.put("requestedCredit", credit.requestedCredit());
            audit.put("effectiveCredit", credit.effectiveCredit());
            audit.put("unusedCredit", credit.unusedCredit());
            audit.put("grossDecliningLineTCV", adjustment.grossTcv);
            audit.put("firstGrossPeriodAmount", firstGross);
            declining.getMetadata().put("migrationCreditAudit", audit);
        }
    }

    private static List<Interval> entitlement(List<RampStep> schedule, int term, String role) {
        List<Interval> result = new ArrayList<>();
        List<RampStep> ordered = schedule.stream().sorted(Comparator.comparing(step ->
                step == null || step.getStartMonth() == null ? Integer.MAX_VALUE : step.getStartMonth())).toList();
        Integer previousQuantity = null;
        for (RampStep step : ordered) {
            if (step == null || step.getStartMonth() == null || step.getStartMonth() < 1
                    || step.getMonths() == null || step.getMonths() < 1 || step.getQuantity() == null || step.getQuantity() < 0
                    || (long) step.getStartMonth() + step.getMonths() > (long) term + 1) {
                throw invalid("Invalid migration entitlement period");
            }
            if (previousQuantity != null && ("RISING".equals(role)
                    ? step.getQuantity() < previousQuantity : step.getQuantity() > previousQuantity)) {
                throw invalid("Migration quantity direction disagrees with authorized role");
            }
            previousQuantity = step.getQuantity();
            if (step.getQuantity() > 0) result.add(new Interval(step.getStartMonth(), step.getStartMonth() + step.getMonths()));
        }
        return List.copyOf(result);
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid("Missing migration " + name);
        return text;
    }

    private static BigDecimal number(Object value, String name) {
        if (!(value instanceof Number)) throw invalid("Migration " + name + " must be numeric");
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw invalid("Invalid migration " + name); }
    }

    private static int positiveInteger(Object value, String name) {
        try {
            int integer = number(value, name).intValueExact();
            if (integer < 1) throw invalid("Migration " + name + " must be positive");
            return integer;
        } catch (ArithmeticException error) { throw invalid("Migration " + name + " must be a whole month"); }
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(reason, HttpStatus.UNPROCESSABLE_CONTENT, "RAMP_SCHEDULE_INVALID");
    }

    private record Interval(int start, int end) {}
    private record Pair(Member declining, Member rising) {}
    private record Adjustment(Pair pair, PricedPeriod first, BigDecimal grossTcv, MigrationCreditPolicy.Credit credit) {}
    private record Member(int index, String productId, ProductType productType, String currency,
                          String pairId, String role, int start, int months, BigDecimal book, List<Interval> entitlement) {
        Map<String, Object> input() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("pairId", pairId); input.put("role", role); input.put("startMonth", start); input.put("months", months);
            if (book != null) input.put("bookValue", book);
            return input;
        }
    }

    /** Construction is private: callers cannot turn free-form metadata into a governance exemption. */
    public static final class Plan {
        private final List<Pair> pairs;
        private final int maxWindowMonths;

        private Plan(List<Pair> pairs, int maxWindowMonths) {
            this.pairs = pairs;
            this.maxWindowMonths = maxWindowMonths;
        }

        public static Plan empty() { return new Plan(List.of(), 12); }

        private Member member(int lineIndex) {
            for (Pair pair : pairs) {
                if (pair.declining.index == lineIndex) return pair.declining;
                if (pair.rising.index == lineIndex) return pair.rising;
            }
            return null;
        }

        public boolean hasLine(int lineIndex) { return member(lineIndex) != null; }

        public Map<String, Object> input(int lineIndex) {
            Member member = member(lineIndex);
            return member == null ? Map.of() : member.input();
        }

        public boolean allowsStepDown(int lineIndex, PricedPeriod period) {
            Member member = member(lineIndex);
            if (member == null || !member.role.equals("DECLINING")) return false;
            int start = period.getStartMonth(), end = member.start + member.months;
            return start >= member.start && start < end || start == end && period.getQuantity() == 0;
        }

        public String policyContext(int lineIndex) {
            Member member = member(lineIndex);
            if (member == null) return "";
            Map<String, Object> evidence = new LinkedHashMap<>();
            Map<String, Object> input = member.input();
            if (member.book != null) input.put("bookValue", member.book.stripTrailingZeros().toPlainString());
            evidence.put("hybridMigration", input);
            evidence.put("maxMigrationWindowMonths", maxWindowMonths);
            evidence.put("productExternalId", member.productId);
            evidence.put("productType", member.productType.name());
            evidence.put("productCurrency", member.currency);
            try { return JSON.writeValueAsString(evidence); }
            catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode migration policy evidence", error); }
        }
    }
}
