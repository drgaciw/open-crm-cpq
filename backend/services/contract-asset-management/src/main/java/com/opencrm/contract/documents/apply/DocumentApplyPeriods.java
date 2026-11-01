package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Add, Update, and Remove against rate periods. Amounts are copied. Nothing is repriced. */
final class DocumentApplyPeriods {

    private DocumentApplyPeriods() {
    }

    static String blockReason(AmendmentApplicationUnit unit, Subscription subscription, List<ApplyLine> lines,
                              List<SubscriptionRatePeriod> periods, String observedPricebook) {
        if (subscription == null || !Objects.equals(subscription.getCommercialRevision(), unit.getObservedCommercialRevision())) {
            return "V3";
        }
        for (ApplyLine line : lines) {
            String lineReason = lineReason(line, periods, observedPricebook);
            if (lineReason != null) {
                return lineReason;
            }
        }
        return null;
    }

    static String detail(String code) {
        return switch (code) {
            case "V3" -> "observed commercial revision does not match the subscription";
            case "V4" -> "source accepted line is missing or retired";
            case "V9" -> "pinned pricebook version differs at apply";
            case "CO_TERM_MISSING" -> "the new period has no co-term end date";
            case "ACTION_NOT_IN_SLICE" -> "the action is outside Add, Update, and Remove";
            default -> code;
        };
    }

    static void write(Subscription subscription, int revisionNo, List<ApplyLine> lines,
                      List<SubscriptionRatePeriod> periods, SubscriptionRatePeriodRepository rates,
                      DocumentApplyGateway gateway) {
        for (ApplyLine line : lines) {
            switch (line.action()) {
                case ADD -> open(subscription, revisionNo, line, null, null, rates, gateway);
                case UPDATE -> {
                    SubscriptionRatePeriod source = close(line, periods, rates);
                    open(subscription, revisionNo, line, source, line.sourceAcceptedLineUuid(), rates, gateway);
                }
                case REMOVE -> close(line, periods, rates);
                case NO_CHANGE, RENEW, UPGRADE, DOWNGRADE, SWAP, CONVERT_METRIC, SUSPEND, RESUME, TERMINATE ->
                        throw new IllegalStateException("blocked actions are not written");
            }
        }
    }

    private static String lineReason(ApplyLine line, List<SubscriptionRatePeriod> periods, String observedPricebook) {
        return switch (line.action()) {
            case ADD, UPDATE, REMOVE -> sliceReason(line, periods, observedPricebook);
            case NO_CHANGE, RENEW, UPGRADE, DOWNGRADE, SWAP, CONVERT_METRIC, SUSPEND, RESUME, TERMINATE ->
                    "ACTION_NOT_IN_SLICE";
        };
    }

    private static String sliceReason(ApplyLine line, List<SubscriptionRatePeriod> periods, String observedPricebook) {
        if ((line.action() == ActionCode.UPDATE || line.action() == ActionCode.REMOVE)
                && openSources(periods, line).isEmpty()) {
            return "V4";
        }
        if (observedPricebook != null && !observedPricebook.equals(line.pricebookVersion())) {
            return "V9";
        }
        if (line.action() != ActionCode.REMOVE && line.effectiveTo() == null) {
            return "CO_TERM_MISSING";
        }
        return null;
    }

    private static SubscriptionRatePeriod close(ApplyLine line, List<SubscriptionRatePeriod> periods,
                                                SubscriptionRatePeriodRepository rates) {
        SubscriptionRatePeriod first = null;
        LocalDate effective = line.effectiveFrom();
        for (SubscriptionRatePeriod old : openSources(periods, line)) {
            old.setEffectiveEndDate(effective.minusDays(1));
            if (!old.getStartDate().isBefore(effective)) {
                old.setRetired(true);
            }
            rates.save(old);
            if (first == null) {
                first = old;
            }
        }
        return first;
    }

    private static void open(Subscription subscription, int revisionNo, ApplyLine line, SubscriptionRatePeriod source,
                             String sourceLine, SubscriptionRatePeriodRepository rates, DocumentApplyGateway gateway) {
        LocalDate start = line.effectiveFrom();
        LocalDate anchor = source == null || source.getBillingAnchorDate() == null ? start : source.getBillingAnchorDate();
        BigDecimal price = source != null && source.getNetUnitPrice() != null ? source.getNetUnitPrice() : line.unitPrice();
        BigDecimal monthly = source == null ? null : source.getNetMonthly();
        SubscriptionRatePeriod created = SubscriptionRatePeriod.builder()
                .subscriptionId(subscription.getId())
                .acceptedLineUuid(UUID.randomUUID().toString())
                .sourceLineUuid(sourceLine)
                .productExternalId(line.productExternalId())
                .productName(line.productExternalId())
                .sequence(1)
                .startDate(start)
                .billingAnchorDate(anchor)
                .endDate(line.effectiveTo())
                .effectiveEndDate(line.effectiveTo())
                .quantity(line.endQty())
                .netUnitPrice(price)
                .netMonthly(monthly)
                .provenance("DOCUMENT_APPLY")
                .build();
        // Flush before the JDBC trace update. JdbcTemplate does not see an unflushed INSERT,
        // so the line id would stay null after commit.
        rates.saveAndFlush(created);
        gateway.bindAmendmentLine(created.getId(), line.id(), revisionNo);
    }

    private static List<SubscriptionRatePeriod> openSources(List<SubscriptionRatePeriod> periods, ApplyLine line) {
        return periods.stream()
                .filter(period -> !period.isDeleted() && !period.isRetired())
                .filter(period -> line.sourceAcceptedLineUuid() != null
                        && line.sourceAcceptedLineUuid().equals(period.getAcceptedLineUuid()))
                // A null effective end is open-ended coverage, so it still matches.
                .filter(period -> {
                    LocalDate effectiveEnd = period.getEffectiveEndDate();
                    return effectiveEnd == null || !effectiveEnd.isBefore(line.effectiveFrom());
                })
                .toList();
    }
}
