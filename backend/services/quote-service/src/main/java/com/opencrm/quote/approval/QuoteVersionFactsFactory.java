package com.opencrm.quote.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.QuotePolicyEvaluationRequest;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.RampScheduleViews;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quote version approval-routing evaluates. Discount is a percentage. When the schedule is current,
 * the value is the highest line or ramp-period discount, so a back-loaded period is not hidden by a blend.
 * {@code estimatedMarginPct} is not read: the pricing placeholder 40.0 is not a margin. A margin is taken only
 * from {@code marginPercent} on the quote metadata or pricing snapshot.
 */
@Component
public class QuoteVersionFactsFactory {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final ObjectMapper objectMapper;

    public QuoteVersionFactsFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public QuotePolicyEvaluationRequest from(Quote quote) {
        JsonNode metadata = object(quote.getMetadata());
        JsonNode waterfall = object(quote.getPricingWaterfallJson());
        return QuotePolicyEvaluationRequest.builder()
                .quoteId(String.valueOf(quote.getId()))
                .quoteVersion(quote.getVersion())
                .tenantId(quote.getTenantId())
                .segment(text(metadata, "segment"))
                .productFamily(productFamily(quote))
                .region(quote.getRegion())
                .discountPercent(discountPercent(quote))
                .marginPercent(margin(metadata, waterfall))
                .termMonths(quote.getDurationInMonths())
                .clauseCodes(clauses(metadata))
                .asOf(LocalDate.now(ZoneOffset.UTC))
                .build();
    }

    private BigDecimal discountPercent(Quote quote) {
        BigDecimal maxFraction = BigDecimal.ZERO;
        List<QuoteLineItem> lines = quote.getLineItems() == null ? List.of() : quote.getLineItems();
        for (QuoteLineItem line : lines) {
            if (line == null || line.isDeleted()) {
                continue;
            }
            maxFraction = max(maxFraction, line.getDiscountRate());
            if (!quote.isRampScheduleStale()) {
                for (QuoteRampStep step : RampScheduleViews.activeSteps(line)) {
                    maxFraction = max(maxFraction, step.getDiscountFraction());
                }
            }
        }
        if (maxFraction.compareTo(BigDecimal.ONE) > 0) {
            return maxFraction.stripTrailingZeros();
        }
        return maxFraction.multiply(ONE_HUNDRED).stripTrailingZeros();
    }

    private static BigDecimal max(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return candidate.compareTo(current) > 0 ? candidate : current;
    }

    private static BigDecimal margin(JsonNode metadata, JsonNode waterfall) {
        BigDecimal declared = decimal(metadata.get("marginPercent"));
        if (declared == null) {
            declared = decimal(waterfall.get("marginPercent"));
        }
        return declared;
    }

    private static String productFamily(Quote quote) {
        String family = null;
        List<QuoteLineItem> lines = quote.getLineItems() == null ? List.of() : quote.getLineItems();
        for (QuoteLineItem line : lines) {
            if (line == null || line.isDeleted() || line.getProductType() == null || line.getProductType().isBlank()) {
                continue;
            }
            if (family == null) {
                family = line.getProductType().trim();
            } else if (!family.equalsIgnoreCase(line.getProductType().trim())) {
                return null;
            }
        }
        return family;
    }

    private static List<String> clauses(JsonNode metadata) {
        JsonNode node = metadata.get("nonStandardClauses");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                codes.add(item.asText().trim());
            }
        });
        return codes;
    }

    private JsonNode object(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw unreadable();
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw unreadable();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ex) {
                throw unreadable();
            }
        }
        return null;
    }

    private static BusinessException unreadable() {
        return new BusinessException(
                "Quote commercial snapshot is unreadable",
                HttpStatus.CONFLICT,
                "POLICY_QUOTE_UNREADABLE");
    }
}
