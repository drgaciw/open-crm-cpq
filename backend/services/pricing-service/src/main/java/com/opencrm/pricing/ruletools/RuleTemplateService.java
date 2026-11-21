package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.rule.RuleCreateRequest;
import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleService;
import com.opencrm.pricing.rule.RuleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates CPQ-fact DRL from price-dools template kinds and creates a DRAFT rule.
 * Does not activate the parent rule set.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RuleTemplateService {

    private static final Pattern SAFE_NAME = Pattern.compile("[^A-Za-z0-9 _-]");
    private static final Pattern SAFE_NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private final RuleService ruleService;

    public RuleDto apply(RuleTemplateKind kind, RuleTemplateApplyRequest request) {
        String ruleName = sanitizeName(request.getName() == null || request.getName().isBlank()
                ? defaultName(kind)
                : request.getName());
        String drl = render(kind, ruleName, request.getParameters() == null ? Map.of() : request.getParameters());
        RuleCreateRequest create = RuleCreateRequest.builder()
                .ruleSetId(request.getRuleSetId())
                .name(ruleName)
                .description(request.getDescription() == null
                        ? "Generated from template " + kind.path() + "; DRAFT only"
                        : request.getDescription())
                .ruleType(ruleType(kind))
                .priority(request.getPriority() == null ? defaultPriority(kind) : request.getPriority())
                .content(drl)
                .explanationCode(explanationCode(kind))
                .build();
        return ruleService.create(create);
    }

    String render(RuleTemplateKind kind, String ruleName, Map<String, Object> parameters) {
        return switch (kind) {
            case VOLUME_DISCOUNT -> volumeDiscount(ruleName, parameters);
            case COMMITMENT -> commitment(ruleName, parameters);
            case RISK_ADJUSTMENT -> riskAdjustment(ruleName, parameters);
            case CONTRACT_LENGTH -> contractLength(ruleName, parameters);
        };
    }

    private String volumeDiscount(String ruleName, Map<String, Object> parameters) {
        String minQty = number(parameters, "minQuantity", "100");
        String discount = number(parameters, "discountRate", "0.07");
        return header()
                + "rule \"" + ruleName + "\"\n"
                + "salience 620\n"
                + "when\n"
                + "    $qli: QuoteLineItemFact(lineItemType == \"SAAS_SEAT_NAMED\", $qty: quantity, $list: listPrice)\n"
                + "    $quote: QuoteFact(this == $qli.quote, durationInMonths >= 12)\n"
                + "then\n"
                + "    if ($qty >= " + minQty + ") {\n"
                + "        BigDecimal discount = new BigDecimal(\"" + discount + "\");\n"
                + "        $qli.setDiscountRate(discount);\n"
                + "        $qli.setFinalPrice($list.multiply(BigDecimal.ONE.subtract(discount)));\n"
                + "    }\n"
                + "end\n";
    }

    private String commitment(String ruleName, Map<String, Object> parameters) {
        String minAcv = number(parameters, "minAcv", "50000");
        return header()
                + "rule \"" + ruleName + "\"\n"
                + "salience 545\n"
                + "when\n"
                + "    $quote: QuoteFact(finalPrice != null, $finalPrice: finalPrice)\n"
                + "    eval($quote.getDecimal(\"acv\") != null)\n"
                + "then\n"
                + "    BigDecimal acv = $quote.getDecimal(\"acv\");\n"
                + "    BigDecimal minCommitment = new BigDecimal(\"" + minAcv + "\");\n"
                + "    if (acv.compareTo(minCommitment) < 0 && $finalPrice.compareTo(minCommitment) < 0) {\n"
                + "        $quote.setFinalPrice(minCommitment);\n"
                + "        $quote.setDecimal(\"minCommitment\", minCommitment);\n"
                + "        $quote.setString(\"explanationCode\", \"ACV_MIN_COMMITMENT\");\n"
                + "    }\n"
                + "end\n";
    }

    private String riskAdjustment(String ruleName, Map<String, Object> parameters) {
        String threshold = number(parameters, "churnThreshold", "70");
        String adjustment = number(parameters, "adjustment", "-0.05");
        return header()
                + "rule \"" + ruleName + "\"\n"
                + "salience 535\n"
                + "when\n"
                + "    $quote: QuoteFact(finalPrice != null, $finalPrice: finalPrice, customer != null, $cust: customer)\n"
                + "    eval($cust.getChurnScore() != null && !\"RENEWAL\".equals($quote.getString(\"dealType\")))\n"
                + "then\n"
                + "    if ($cust.getChurnScore() > " + threshold + ") {\n"
                + "        BigDecimal churnAdjustment = new BigDecimal(\"" + adjustment + "\");\n"
                + "        $quote.setFinalPrice($finalPrice.multiply(BigDecimal.ONE.add(churnAdjustment)));\n"
                + "        $quote.setDecimal(\"churnAdjustment\", churnAdjustment);\n"
                + "        $quote.setString(\"explanationCode\", \"CHURN_ADJUSTMENT\");\n"
                + "    }\n"
                + "end\n";
    }

    private String contractLength(String ruleName, Map<String, Object> parameters) {
        String minMonths = number(parameters, "minMonths", "24");
        String discount = number(parameters, "discountRate", "0.05");
        return header()
                + "rule \"" + ruleName + "\"\n"
                + "salience 560\n"
                + "when\n"
                + "    $qli: QuoteLineItemFact($list: listPrice)\n"
                + "    $quote: QuoteFact(this == $qli.quote, durationInMonths >= " + minMonths + ")\n"
                + "then\n"
                + "    BigDecimal discount = new BigDecimal(\"" + discount + "\");\n"
                + "    $qli.setDiscountRate(discount);\n"
                + "    $qli.setFinalPrice($list.multiply(BigDecimal.ONE.subtract(discount)));\n"
                + "end\n";
    }

    private static String header() {
        return """
                package com.opencrm.pricing.rules;
                import com.opencrm.rules.model.QuoteFact;
                import com.opencrm.rules.model.QuoteLineItemFact;
                import com.opencrm.rules.model.CustomerFact;
                import java.math.BigDecimal;

                """;
    }

    private static String defaultName(RuleTemplateKind kind) {
        return switch (kind) {
            case VOLUME_DISCOUNT -> "Template Volume Discount";
            case COMMITMENT -> "Template Minimum Commitment";
            case RISK_ADJUSTMENT -> "Template Risk Adjustment";
            case CONTRACT_LENGTH -> "Template Contract Length";
        };
    }

    private static int defaultPriority(RuleTemplateKind kind) {
        return switch (kind) {
            case VOLUME_DISCOUNT -> 620;
            case COMMITMENT -> 545;
            case RISK_ADJUSTMENT -> 535;
            case CONTRACT_LENGTH -> 560;
        };
    }

    private static RuleType ruleType(RuleTemplateKind kind) {
        return switch (kind) {
            case VOLUME_DISCOUNT, COMMITMENT, CONTRACT_LENGTH -> RuleType.PRICING;
            case RISK_ADJUSTMENT -> RuleType.RISK;
        };
    }

    private static String explanationCode(RuleTemplateKind kind) {
        return switch (kind) {
            case VOLUME_DISCOUNT -> "TEMPLATE_VOLUME_DISCOUNT";
            case COMMITMENT -> "TEMPLATE_MIN_COMMITMENT";
            case RISK_ADJUSTMENT -> "TEMPLATE_RISK_ADJUSTMENT";
            case CONTRACT_LENGTH -> "TEMPLATE_CONTRACT_LENGTH";
        };
    }

    static String sanitizeName(String name) {
        String cleaned = SAFE_NAME.matcher(name).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            throw new BusinessException("Rule name must contain letters or digits", "INVALID_TEMPLATE_NAME");
        }
        return cleaned;
    }

    static String number(Map<String, Object> parameters, String key, String defaultValue) {
        Object raw = parameters.get(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        if (!SAFE_NUMBER.matcher(value).matches()) {
            throw new BusinessException("Parameter " + key + " must be numeric", "INVALID_TEMPLATE_PARAMETER");
        }
        return value;
    }
}
