package com.opencrm.pricing.cost;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The usable cost basis of one product, as answered by the lookup API (ADR-006 DTO; never the entity).
 *
 * @param productExternalId the catalog product id
 * @param unitCost          the sum of {@code components}, scale 4, per {@code billingUnit} (per month for recurring)
 * @param currency          ISO 4217
 * @param billingUnit       as exported
 * @param effectiveFrom     the effective date of the rows used: the newest on or before {@code effectiveOn}
 * @param version           the highest export version among the rows used
 * @param effectiveOn       the date that selected the rows (a ramp period start)
 * @param pricedOn          the date freshness was judged on
 * @param components        each component's cost, in {@link CostComponent} order
 */
public record CostBasisView(
        String productExternalId,
        BigDecimal unitCost,
        String currency,
        String billingUnit,
        LocalDate effectiveFrom,
        long version,
        LocalDate effectiveOn,
        LocalDate pricedOn,
        List<Component> components) {

    public CostBasisView {
        components = List.copyOf(components);
    }

    /** One component of the cost. */
    public record Component(CostComponent component, BigDecimal unitCost) {
    }

    static CostBasisView of(UnitCost cost, LocalDate effectiveOn, LocalDate pricedOn) {
        List<Component> components = cost.components().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Component(entry.getKey(), entry.getValue()))
                .toList();
        return new CostBasisView(cost.productExternalId(), cost.unitCost(), cost.currency(), cost.billingUnit(),
                cost.effectiveFrom(), cost.version(), effectiveOn, pricedOn, components);
    }
}
