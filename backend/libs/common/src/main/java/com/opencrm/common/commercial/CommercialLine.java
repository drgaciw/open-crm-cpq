package com.opencrm.common.commercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One commercial line in the CAM-COMMERCIAL-1 projection. Pricing pins are null until a priced write. */
public record CommercialLine(
        UUID changeLineRef,
        int lineNo,
        String actionCode,
        String businessCategory,
        String applyTargetType,
        String quantitySemantics,
        BigDecimal startQty,
        BigDecimal deltaQty,
        BigDecimal endQty,
        String metric,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String chargeType,
        String priceAsOf,
        String policySlot,
        int applicationUnitNo,
        long observedCommercialRevision,
        UUID targetSubscriptionId,
        String sourceAcceptedLineUuid,
        String productExternalId,
        String pricebookVersion,
        String configurationHash,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        String policyId,
        String policyVersion,
        String policyDecisionId,
        String pricingTraceId,
        BigDecimal overrideValue,
        String overrideReasonCode,
        String overrideActor) {

    public static CommercialLine structural(
            UUID changeLineRef, int lineNo, String actionCode, String businessCategory, String applyTargetType,
            BigDecimal startQty, BigDecimal deltaQty, BigDecimal endQty, int applicationUnitNo,
            long observedCommercialRevision) {
        return new CommercialLine(changeLineRef, lineNo, actionCode, businessCategory, applyTargetType,
                null, startQty, deltaQty, endQty, null, null, null, null, null, null,
                applicationUnitNo, observedCommercialRevision, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    List<JcsCanonicalizer.Value.Field> fields() {
        List<JcsCanonicalizer.Value.Field> fields = new ArrayList<>();
        fields.add(field("actionCode", text(actionCode)));
        fields.add(field("applicationUnitNo", new JcsCanonicalizer.Value.IntNum(applicationUnitNo)));
        fields.add(field("applyTargetType", text(applyTargetType)));
        fields.add(field("businessCategory", text(businessCategory)));
        fields.add(field("changeLineRef", text(changeLineRef == null ? null : changeLineRef.toString())));
        fields.add(field("chargeType", text(chargeType)));
        fields.add(field("configurationHash", text(configurationHash)));
        fields.add(field("deltaQty", decimal(deltaQty)));
        fields.add(field("effectiveFrom", text(effectiveFrom == null ? null : effectiveFrom.toString())));
        fields.add(field("effectiveTo", text(effectiveTo == null ? null : effectiveTo.toString())));
        fields.add(field("endQty", decimal(endQty)));
        fields.add(field("lineNo", new JcsCanonicalizer.Value.IntNum(lineNo)));
        fields.add(field("metric", text(metric)));
        fields.add(field("netAmount", decimal(netAmount)));
        fields.add(field("observedCommercialRevision", new JcsCanonicalizer.Value.IntNum(observedCommercialRevision)));
        fields.add(field("overrideActor", text(overrideActor)));
        fields.add(field("overrideReasonCode", text(overrideReasonCode)));
        fields.add(field("overrideValue", decimal(overrideValue)));
        fields.add(field("policyDecisionId", text(policyDecisionId)));
        fields.add(field("policyId", text(policyId)));
        fields.add(field("policySlot", text(policySlot)));
        fields.add(field("policyVersion", text(policyVersion)));
        fields.add(field("priceAsOf", text(priceAsOf)));
        fields.add(field("pricebookVersion", text(pricebookVersion)));
        fields.add(field("pricingTraceId", text(pricingTraceId)));
        fields.add(field("productExternalId", text(productExternalId)));
        fields.add(field("quantitySemantics", text(quantitySemantics)));
        fields.add(field("sourceAcceptedLineUuid", text(sourceAcceptedLineUuid)));
        fields.add(field("startQty", decimal(startQty)));
        fields.add(field("targetSubscriptionId", text(targetSubscriptionId == null ? null : targetSubscriptionId.toString())));
        fields.add(field("unitPrice", decimal(unitPrice)));
        return fields;
    }

    private static JcsCanonicalizer.Value.Field field(String name, JcsCanonicalizer.Value value) {
        return new JcsCanonicalizer.Value.Field(name, value);
    }

    private static JcsCanonicalizer.Value text(String value) {
        return value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.Str(value);
    }

    private static JcsCanonicalizer.Value decimal(BigDecimal value) {
        return value == null ? new JcsCanonicalizer.Value.Nul() : new JcsCanonicalizer.Value.Str(DecimalStrings.normalize(value));
    }
}
