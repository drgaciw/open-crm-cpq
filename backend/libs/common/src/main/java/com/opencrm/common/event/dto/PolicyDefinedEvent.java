package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Emitted by approval-routing when a commercial policy version is defined (BC-04-01, DRG-441): a declarative set of
 * {@link ThresholdDefined} rules on discount %, margin floor, term and non-standard clauses, scoped by segment,
 * product family and region.
 *
 * <p>{@code policyVersion} is the local integer version described on {@code CommercialPolicy} (BC-18 cross-service
 * configuration/model versioning is not landed, so this is not a promoted BC-18 revision). {@code supersedesPolicyId}
 * is null for the first version of a policy; otherwise it names the earlier version this one replaces, which is
 * deprecated in the same transaction that defines this one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PolicyDefinedEvent(
        Long policyId,
        String policyUuid,
        String name,
        Integer policyVersion,
        Long supersedesPolicyId,
        String segment,
        String productFamily,
        String region,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        List<ThresholdDefined> thresholds) {

    /** One declarative threshold on the defined policy version. */
    public record ThresholdDefined(
            String thresholdType,
            BigDecimal limitValue,
            String clauseCode,
            String description) {
    }
}
