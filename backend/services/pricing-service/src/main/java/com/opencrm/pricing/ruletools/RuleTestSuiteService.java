package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Suite runner over {@link PricingExecutionService#simulate}; no parallel Drools engine (PD1-3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleTestSuiteService {

    private final PricingExecutionService pricingExecutionService;

    public RuleTestSuiteResultDto run(RuleTestSuiteRunRequest request) {
        String suiteName = request.getSuiteName() == null || request.getSuiteName().isBlank()
                ? "custom"
                : request.getSuiteName().trim();
        List<RuleTestCaseRequest> cases = resolveCases(suiteName, request.getCases());
        List<RuleTestCaseResultDto> results = new ArrayList<>();
        int passed = 0;
        for (RuleTestCaseRequest testCase : cases) {
            RuleTestCaseResultDto result = runCase(testCase);
            results.add(result);
            if (result.isPassed()) {
                passed++;
            }
        }
        int total = results.size();
        return RuleTestSuiteResultDto.builder()
                .suiteName(suiteName)
                .passedTests(passed)
                .totalTests(total)
                .passRate(total == 0 ? 0d : (double) passed / total)
                .cases(results)
                .build();
    }

    private List<RuleTestCaseRequest> resolveCases(String suiteName, List<RuleTestCaseRequest> supplied) {
        if (supplied != null && !supplied.isEmpty()) {
            return supplied;
        }
        String key = suiteName.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "volume-discount" -> List.of(fixture("volume-discount-baseline", "fixture-volume"));
            case "min-commitment" -> List.of(fixture("min-commitment-baseline", "fixture-commitment"));
            case "churn-risk" -> List.of(fixture("churn-risk-baseline", "fixture-churn"));
            case "all" -> List.of(
                    fixture("volume-discount-baseline", "fixture-volume"),
                    fixture("min-commitment-baseline", "fixture-commitment"),
                    fixture("churn-risk-baseline", "fixture-churn"));
            case "custom" -> throw new BusinessException(
                    "Test suite requires cases when suiteName is custom", "EMPTY_TEST_SUITE");
            default -> throw new BusinessException("Unknown test suite: " + suiteName, "UNKNOWN_TEST_SUITE");
        };
    }

    private RuleTestCaseResultDto runCase(RuleTestCaseRequest testCase) {
        Map<String, Object> expected = testCase.getExpected() == null ? Map.of() : testCase.getExpected();
        try {
            PricingResult simulation = pricingExecutionService.simulate(testCase.getContext());
            Map<String, Object> actual = actuals(simulation);
            if (simulation == null || !simulation.isSuccess()) {
                return RuleTestCaseResultDto.builder()
                        .name(testCase.getName())
                        .passed(false)
                        .errorMessage(simulation == null ? "Simulation returned null" : simulation.getErrorMessage())
                        .expected(expected)
                        .actual(actual)
                        .build();
            }
            String mismatch = compare(expected, actual);
            return RuleTestCaseResultDto.builder()
                    .name(testCase.getName())
                    .passed(mismatch == null)
                    .errorMessage(mismatch)
                    .expected(expected)
                    .actual(actual)
                    .build();
        } catch (RuntimeException ex) {
            return RuleTestCaseResultDto.builder()
                    .name(testCase.getName())
                    .passed(false)
                    .errorMessage(ex.getMessage())
                    .expected(expected)
                    .actual(Map.of())
                    .build();
        }
    }

    private static Map<String, Object> actuals(PricingResult simulation) {
        Map<String, Object> actual = new LinkedHashMap<>();
        if (simulation == null) {
            return actual;
        }
        actual.put("success", simulation.isSuccess());
        FinancialMetrics metrics = simulation.getFinancialMetrics();
        if (metrics == null) {
            return actual;
        }
        actual.put("arr", metrics.getArr());
        actual.put("tcv", metrics.getTcv());
        actual.put("acv", metrics.getAcv());
        actual.put("clv", metrics.getClv());
        actual.put("churnScore", metrics.getChurnScore());
        actual.put("discountRate", metrics.getDiscountRate());
        return actual;
    }

    private static String compare(Map<String, Object> expected, Map<String, Object> actual) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object got = actual.get(entry.getKey());
            if (!valuesMatch(entry.getValue(), got)) {
                return "Expected " + entry.getKey() + "=" + entry.getValue() + " but was " + got;
            }
        }
        return null;
    }

    private static boolean valuesMatch(Object expected, Object actual) {
        if (expected == null) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        if (expected instanceof Number || actual instanceof Number || looksNumeric(expected) || looksNumeric(actual)) {
            try {
                return new BigDecimal(expected.toString()).compareTo(new BigDecimal(actual.toString())) == 0;
            } catch (NumberFormatException ex) {
                return expected.toString().equals(actual.toString());
            }
        }
        return String.valueOf(expected).equals(String.valueOf(actual));
    }

    private static boolean looksNumeric(Object value) {
        return value instanceof String s && SAFE_NUMBER.matcher(s.trim()).matches();
    }

    private static final java.util.regex.Pattern SAFE_NUMBER = java.util.regex.Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private static RuleTestCaseRequest fixture(String name, String quoteExternalId) {
        return RuleTestCaseRequest.builder()
                .name(name)
                .context(PricingContext.builder()
                        .customerExternalId("fixture-customer")
                        .quoteExternalId(quoteExternalId)
                        .durationInMonths(12)
                        .currency("USD")
                        .build())
                .expected(Map.of())
                .build();
    }
}
