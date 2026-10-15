package com.opencrm.pricing.cost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The stored cost basis behind {@link CostBasisLookup}. Being a component, it replaces the empty fallback of
 * {@link CostBasisConfiguration}.
 *
 * <p>For a product, a period starting {@code effectiveOn}, priced on {@code pricedOn}:
 * <ol>
 *   <li><b>selection:</b> the governing date {@code E} is the newest {@code effectiveFrom <= effectiveOn}; none gives
 *       empty. A Finance-approved row dated after {@code pricedOn} therefore governs the periods that start on or after
 *       its date;</li>
 *   <li><b>freshness:</b> the newest date {@code F <= pricedOn} must not be
 *       {@link CostBasisStaleness#isStale(LocalDate, LocalDate) stale on pricedOn}, else empty, whatever the period.
 *       Staleness is never judged against {@code effectiveOn}: a three-year ramp priced today on a fresh basis keeps its
 *       cost for every period, and a basis stale today fails every period, past or future. When no row is effective on
 *       {@code pricedOn} at all, only forward-dated rows exist; they cannot be stale yet, so the selection stands;</li>
 *   <li>for each component on {@code E}, the row of the highest version; components of older dates are not mixed in;</li>
 *   <li>those rows must share billing unit and currency, else empty (fail closed);</li>
 *   <li>{@code unitCost} is their sum at scale 4; {@code version} is the highest version among them.</li>
 * </ol>
 * The 2-argument {@link CostBasisLookup#find(String, LocalDate)} is the port's default: both dates equal.
 *
 * <p><b>Retiring a component.</b> A newer version, or a newer effective date, that omits a component does not remove
 * it: within one effective date each component keeps its own highest version, so an omitted component keeps its older
 * row. To retire a component on a date, export a row for it with {@code unitCost} 0. A newer effective date replaces
 * the whole set, so a component absent from that date is simply not part of the cost from then on.
 *
 * <p>Rows are append-only and the selection is a pure function of them, so the same stored rows and inputs always give
 * an equal {@link UnitCost}.
 */
@Slf4j
@Component
public class JpaCostBasisLookup implements CostBasisLookup {

    private final CostBasisRowRepository rows;

    public JpaCostBasisLookup(CostBasisRowRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitCost> find(String productExternalId, LocalDate effectiveOn, LocalDate pricedOn) {
        if (productExternalId == null || productExternalId.isBlank() || effectiveOn == null || pricedOn == null) {
            return Optional.empty();
        }
        Optional<LocalDate> governing = newestOnOrBefore(productExternalId, effectiveOn);
        if (governing.isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalDate> currentOnPricingDate = pricedOn.equals(effectiveOn)
                ? governing : newestOnOrBefore(productExternalId, pricedOn);
        if (currentOnPricingDate.isPresent() && CostBasisStaleness.isStale(currentOnPricingDate.get(), pricedOn)) {
            return Optional.empty();
        }
        LocalDate effectiveFrom = governing.get();

        Map<CostComponent, CostBasisRow> newest = new EnumMap<>(CostComponent.class);
        for (CostBasisRow row : rows.findByProductExternalIdAndEffectiveFrom(productExternalId, effectiveFrom)) {
            newest.merge(row.getComponent(), row, (a, b) -> b.getVersion() > a.getVersion() ? b : a);
        }
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        List<CostBasisRow> selected = List.copyOf(newest.values());
        long units = selected.stream().map(row -> CostBasisIngestionService.normalise(row.getBillingUnit())).distinct().count();
        long currencies = selected.stream().map(CostBasisRow::getCurrency).distinct().count();
        if (units != 1 || currencies != 1) {
            log.warn("Cost basis of {} on {} mixes billing units or currencies; treated as missing",
                    productExternalId, effectiveFrom);
            return Optional.empty();
        }

        Map<CostComponent, BigDecimal> components = new EnumMap<>(CostComponent.class);
        BigDecimal total = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        long version = 0;
        for (CostBasisRow row : selected) {
            BigDecimal cost = row.getUnitCost().setScale(4, RoundingMode.UNNECESSARY);
            components.put(row.getComponent(), cost);
            total = total.add(cost);
            version = Math.max(version, row.getVersion());
        }
        CostBasisRow first = selected.getFirst();
        return Optional.of(new UnitCost(productExternalId, first.getBillingUnit(), total, first.getCurrency(),
                effectiveFrom, version, components));
    }

    /** The newest effective date of any row of the product on or before {@code date}. */
    private Optional<LocalDate> newestOnOrBefore(String productExternalId, LocalDate date) {
        return rows.findFirstByProductExternalIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                productExternalId, date).map(CostBasisRow::getEffectiveFrom);
    }
}
