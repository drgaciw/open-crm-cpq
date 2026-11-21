package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleTestSuiteServiceTest {

    @Mock
    private PricingExecutionService pricingExecutionService;

    @InjectMocks
    private RuleTestSuiteService testSuiteService;

    @Test
    void customCasePassesWhenExpectedMetricsMatchSimulate() {
        when(pricingExecutionService.simulate(any())).thenReturn(PricingResult.builder()
                .success(true)
                .financialMetrics(FinancialMetrics.builder()
                        .arr(new BigDecimal("1200.00"))
                        .tcv(new BigDecimal("2400.00"))
                        .build())
                .build());

        RuleTestSuiteResultDto result = testSuiteService.run(RuleTestSuiteRunRequest.builder()
                .suiteName("custom")
                .cases(List.of(RuleTestCaseRequest.builder()
                        .name("arr-match")
                        .context(PricingContext.builder().quoteExternalId("Q-1").build())
                        .expected(Map.of("arr", "1200.00", "tcv", "2400.00"))
                        .build()))
                .build());

        assertThat(result.getPassedTests()).isEqualTo(1);
        assertThat(result.getTotalTests()).isEqualTo(1);
        assertThat(result.getCases().getFirst().isPassed()).isTrue();
        verify(pricingExecutionService).simulate(any());
    }

    @Test
    void customCaseFailsOnMetricMismatch() {
        when(pricingExecutionService.simulate(any())).thenReturn(PricingResult.builder()
                .success(true)
                .financialMetrics(FinancialMetrics.builder().arr(new BigDecimal("1.00")).build())
                .build());

        RuleTestSuiteResultDto result = testSuiteService.run(RuleTestSuiteRunRequest.builder()
                .cases(List.of(RuleTestCaseRequest.builder()
                        .name("arr-miss")
                        .context(PricingContext.builder().quoteExternalId("Q-1").build())
                        .expected(Map.of("arr", "1200.00"))
                        .build()))
                .build());

        assertThat(result.getPassedTests()).isZero();
        assertThat(result.getCases().getFirst().getErrorMessage()).contains("arr");
    }

    @Test
    void namedVolumeSuiteWrapsSimulate() {
        when(pricingExecutionService.simulate(any())).thenReturn(PricingResult.builder().success(true).build());

        RuleTestSuiteResultDto result = testSuiteService.run(RuleTestSuiteRunRequest.builder()
                .suiteName("volume-discount")
                .build());

        assertThat(result.getSuiteName()).isEqualTo("volume-discount");
        assertThat(result.getTotalTests()).isEqualTo(1);
        assertThat(result.getPassedTests()).isEqualTo(1);
        verify(pricingExecutionService, times(1)).simulate(any());
    }

    @Test
    void unknownSuiteRejected() {
        assertThatThrownBy(() -> testSuiteService.run(RuleTestSuiteRunRequest.builder()
                .suiteName("semantic-cache")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown test suite");
    }
}
