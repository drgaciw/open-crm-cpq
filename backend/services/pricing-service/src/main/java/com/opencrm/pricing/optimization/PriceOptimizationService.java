package com.opencrm.pricing.optimization;

import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BC-03-11: advisory price optimization within a guidance band.
 * Recommendations never replace deterministic policy evaluation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceOptimizationService {

    public static final String MODEL_VERSION = "price-opt-v1";

    private final PricingExecutionService pricingExecutionService;

    public PriceOptimizationResponse optimize(PricingContext context) {
        PricingResult simulation = pricingExecutionService.simulate(context);
        List<AdvisoryPriceElement> recommendations = new ArrayList<>();

        if (simulation.getLineResults() != null) {
            for (PricingLineResult line : simulation.getLineResults()) {
                recommendations.add(recommend(line));
            }
        }

        return PriceOptimizationResponse.builder()
                .simulation(simulation)
                .recommendations(recommendations)
                .modelVersion(MODEL_VERSION)
                .advisory(true)
                .build();
    }

    private AdvisoryPriceElement recommend(PricingLineResult line) {
        BigDecimal list = nullToZero(line.getListPrice());
        BigDecimal unit = nullToZero(line.getUnitPrice() != null ? line.getUnitPrice() : line.getFinalPrice());
        BigDecimal floor = unit.min(list).multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ceiling = list.max(unit).setScale(2, RoundingMode.HALF_UP);
        BigDecimal recommended = unit.add(list).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        if (recommended.compareTo(floor) < 0) {
            recommended = floor;
        }
        if (recommended.compareTo(ceiling) > 0) {
            recommended = ceiling;
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("modelVersion", MODEL_VERSION);
        trace.put("sourceUnitPrice", unit);
        trace.put("sourceListPrice", list);
        trace.put("band", "midpoint-within-10pct-floor");
        trace.put("explanationCodes", line.getExplanationCodes());

        BigDecimal confidence = list.signum() == 0
                ? new BigDecimal("0.50")
                : BigDecimal.ONE.subtract(
                        unit.subtract(list).abs()
                                .divide(list.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP))
                .max(new BigDecimal("0.40"))
                .min(new BigDecimal("0.95"));

        return AdvisoryPriceElement.builder()
                .productExternalId(line.getProductExternalId())
                .listPrice(list)
                .recommendedUnitPrice(recommended)
                .guidanceFloor(floor)
                .guidanceCeiling(ceiling)
                .confidence(confidence)
                .advisory(true)
                .explanationCode("ADVISORY_PRICE_OPT")
                .trace(trace)
                .build();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
