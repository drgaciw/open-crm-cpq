package com.opencrm.margincontextstub;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.UnitCost;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Test-only {@code @Component} adapter, alone in its package so a component scan of this package finds only it. It
 * stands in for any cost basis adapter (the JPA one included) to prove a component replaces the root fallback. The
 * package is deliberately outside {@code com.opencrm.pricing} (and the application's other scan roots), so a full
 * {@code @SpringBootTest} never picks it up beside the real adapter.
 */
@Component
public class StubCostBasisLookup implements CostBasisLookup {

    @Override
    public Optional<UnitCost> find(String productExternalId, LocalDate effectiveOn, LocalDate pricedOn) {
        return Optional.empty();
    }
}
