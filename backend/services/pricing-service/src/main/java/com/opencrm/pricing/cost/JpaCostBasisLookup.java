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
 * <p>For a product on {@code asOf}:
 * <ol>
 *   <li>the governing date {@code E} is the newest {@code effectiveFrom <= asOf}; none gives empty;</li>
 *   <li>if {@link CostBasisStaleness#isStale(LocalDate, LocalDate) E is stale on asOf}, empty;</li>
 *   <li>for each component on {@code E}, the row of the highest version; components of older dates are not mixed in;</li>
 *   <li>those rows must share billing unit and currency, else empty (fail closed);</li>
 *   <li>{@code unitCost} is their sum at scale 4; {@code version} is the highest version among them.</li>
 * </ol>
 * Rows are append-only and the selection is a pure function of them, so the same stored rows and inputs always give an
 * equal {@link UnitCost}.
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
    public Optional<UnitCost> find(String productExternalId, LocalDate asOf) {
        if (productExternalId == null || productExternalId.isBlank() || asOf == null) {
            return Optional.empty();
        }
        Optional<LocalDate> governing = rows
                .findFirstByProductExternalIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(productExternalId, asOf)
                .map(CostBasisRow::getEffectiveFrom);
        if (governing.isEmpty() || CostBasisStaleness.isStale(governing.get(), asOf)) {
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
}
