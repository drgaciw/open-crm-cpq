package com.opencrm.quote.ramp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricedPeriodView;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Quote-owned persisted pricing view and canonical schedule/term/totals binding. */
public final class RampPricingSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RampPricingSnapshot() {}

    public static HybridMigrationContext hybridMigration(QuoteLineItem line) {
        if (line.getHybridMigrationJson() == null) return null;
        try { return JSON.readValue(line.getHybridMigrationJson(), HybridMigrationContext.class); }
        catch (Exception ex) { throw invalid(); }
    }

    public static com.opencrm.common.event.dto.QuoteAcceptedEvent.ProrationSnapshot initialProration(QuoteLineItem line) {
        if (line.getInitialProrationJson() == null || line.getQuote().isRampScheduleStale()) return null;
        try { return JSON.readValue(line.getInitialProrationJson(), com.opencrm.common.event.dto.QuoteAcceptedEvent.ProrationSnapshot.class); }
        catch (Exception ex) { throw invalid(); }
    }

    public static String hybridJson(HybridMigrationContext context) {
        if (context == null) return null;
        try { return JSON.writeValueAsString(context); }
        catch (Exception ex) { throw invalid(); }
    }

    public static List<PricedPeriodView> pricedPeriods(QuoteLineItem line) {
        if (!RampScheduleViews.hasSteps(line) || line.getQuote().isRampScheduleStale()) return null;
        JsonNode node = read(line.getPricingWaterfallJson()).get("pricedPeriods");
        if (node == null || node.isNull()) return null;
        try { return JSON.convertValue(node, new TypeReference<List<PricedPeriodView>>() {}); }
        catch (IllegalArgumentException ex) { throw invalid(); }
    }

    public static RampGovernanceDto governance(Quote quote) {
        if (quote.isRampScheduleStale()) return null;
        JsonNode node = read(quote.getPricingWaterfallJson()).get("rampGovernance");
        try { return node == null || node.isNull() ? null : JSON.convertValue(node, RampGovernanceDto.class); }
        catch (IllegalArgumentException ex) { throw invalid(); }
    }

    public static String fingerprint(Quote quote) {
        JsonNode node = read(quote.getPricingWaterfallJson()).get("pricingFingerprint");
        return node == null || node.isNull() ? null : node.asText();
    }

    public static String fingerprint(Quote quote, List<QuoteLineItem> lines) {
        return hash(quote, lines, true);
    }

    public static String authoringFingerprint(Quote quote, List<QuoteLineItem> lines) {
        return hash(quote, lines, false);
    }

    private static String hash(Quote quote, List<QuoteLineItem> lines, boolean includePricing) {
        List<Object> canonical = new ArrayList<>();
        canonical.add(Arrays.asList(quote.getDurationInMonths(), quote.getStartDate(), quote.getEndDate(),
                quote.getCurrency(), quote.getRegion(), quote.getSubscriptionType(), quote.getCustomerExternalId(),
                quote.getDealType(), quote.getTargetSubscriptionNumber(), quote.getObservedRevision(),
                quote.getSubscriptionEffectiveDate(), normalized(read(quote.getContinuationRequestJson()))));
        if (includePricing) canonical.add(Arrays.asList(number(quote.getTcv()), number(quote.getAcv()),
                number(quote.getArr()), number(quote.getExitArr()), number(quote.getTotalAmount()),
                number(quote.getSubtotal()), number(quote.getTotalDiscount()), number(quote.getTotalTax())));
        lines.stream().filter(line -> !line.isDeleted()).sorted(Comparator.comparing(QuoteLineItem::getId,
                Comparator.nullsFirst(Comparator.naturalOrder()))).forEach(line -> {
            canonical.add(Arrays.asList(line.getId(), line.getUuid(), line.getProductExternalId(), line.getProductType(), line.getLineItemType(),
                    line.getQuantity(), line.getBillingFrequency(), number(line.getListPrice()),
                    line.getSourceSubscriptionLineUuid(), normalized(read(line.getHybridMigrationJson())),
                    normalized(read(line.getInitialProrationJson()))));
            if (includePricing) {
                JsonNode snapshot = read(line.getPricingWaterfallJson());
                canonical.add(Arrays.asList(number(line.getExtendedAmount()), normalized(snapshot.get("rampGovernance")),
                        normalized(snapshot.get("authoritativeProductType")),
                        normalized(snapshot.get("rampPolicyContext")), normalized(snapshot.get("pricedPeriods")),
                        normalized(snapshot.get("initialProrationPolicyContext")), normalized(snapshot.get("initialProrationGovernance")),
                        number(line.getUnitPrice()), number(line.getNetPrice()), number(line.getDiscountRate()),
                        number(line.getDiscountAmount())));
            }
            for (QuoteRampStep step : RampScheduleViews.activeSteps(line)) {
                canonical.add(Arrays.asList(step.getSequence(), step.getStartMonth(), step.getMonths(),
                        step.getQuantity(), step.getPriceBasis(), number(step.getUnitPrice()), number(step.getDiscountFraction())));
            }
        });
        try {
            // String values use JSON encoding; dates have a stable ISO representation.
            byte[] bytes = JSON.writeValueAsBytes(canonical.stream().map(row -> ((List<?>) row).stream()
                    .map(value -> value instanceof java.time.LocalDate ? value.toString() : value).toList()).toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static Object normalized(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.decimalValue().stripTrailingZeros().toPlainString();
        if (value.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            value.fields().forEachRemaining(field -> sorted.put(field.getKey(), normalized(field.getValue())));
            return sorted;
        }
        if (value.isArray()) {
            List<Object> values = new ArrayList<>();
            value.forEach(item -> values.add(normalized(item)));
            return values;
        }
        return value.asText();
    }

    public static JsonNode read(String json) {
        if (json == null || json.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode node = JSON.readTree(json);
            if (node == null || !node.isObject()) throw invalid();
            return node;
        }
        catch (Exception ex) { throw invalid(); }
    }

    private static String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static BusinessException invalid() {
        return new BusinessException("Ramp pricing snapshot is unreadable; recalculate the quote",
                HttpStatus.CONFLICT, "RAMP_PRICING_SNAPSHOT_INVALID");
    }
}
