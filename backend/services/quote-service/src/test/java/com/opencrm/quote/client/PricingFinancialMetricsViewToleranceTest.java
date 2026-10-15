package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-438: pricing's additive margin fields, and any later field, must not break quote calculate. */
@Tag("unit")
class PricingFinancialMetricsViewToleranceTest {

    /** The same configuration {@link PricingServiceClient} parses with: unknown fields fail by default. */
    private final ObjectMapper clientMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("cost-basis metrics bind, keeping estimatedMarginPct a percent and marginFraction a fraction")
    void costBasisMetricsBind() throws Exception {
        PricingExecuteView view = clientMapper.readValue("""
                {"success": true,
                 "financialMetrics": {"tcv": 135600.00, "estimatedMarginPct": 72.17,
                                      "marginFraction": 0.7217, "marginSource": "cost-basis"}}
                """, PricingExecuteView.class);
        PricingFinancialMetricsView metrics = view.getFinancialMetrics();
        assertThat(metrics.getEstimatedMarginPct()).isEqualByComparingTo("72.17");
        assertThat(metrics.getMarginFraction()).isEqualByComparingTo("0.7217");
        assertThat(metrics.getMarginSource()).isEqualTo("cost-basis");
    }

    @Test
    @DisplayName("a missing cost is a null margin, and an unknown future metric is ignored")
    void missingCostAndUnknownField() throws Exception {
        PricingFinancialMetricsView metrics = clientMapper.readValue("""
                {"tcv": 1.00, "estimatedMarginPct": null, "marginSource": "cost-basis", "someFutureMetric": 1}
                """, PricingFinancialMetricsView.class);
        assertThat(metrics.getEstimatedMarginPct()).isNull();
        assertThat(metrics.getMarginFraction()).isNull();
        assertThat(metrics.getTcv()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("today's placeholder payload binds unchanged")
    void placeholderPayload() throws Exception {
        PricingFinancialMetricsView metrics = clientMapper.readValue("""
                {"tcv": 1.00, "estimatedMarginPct": 40.0}
                """, PricingFinancialMetricsView.class);
        assertThat(metrics.getEstimatedMarginPct()).isEqualByComparingTo("40.0");
        assertThat(metrics.getMarginSource()).isNull();
    }
}
