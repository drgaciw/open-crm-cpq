package com.opencrm.common.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-023 boundary fitness for the shared ramp types (ADR-011).
 * The three production sources are a field contract. They must not grow a check, a failure path,
 * or {@link BigDecimal} arithmetic.
 */
@DisplayName("Ramp schedule published language")
class RampStepContractTest {

    private static final List<String> PRODUCTION_FILES = List.of(
            "RampPriceBasis.java",
            "RampStep.java",
            "RampEscalator.java");

    /**
     * Calls that change a {@link BigDecimal}. Construction ({@code new BigDecimal}, {@code valueOf})
     * is not in this list.
     */
    private static final Pattern BIG_DECIMAL_ARITHMETIC = Pattern.compile(
            "\\.(add|subtract|multiply|divide|divideAndRemainder|divideToIntegralValue"
                    + "|plus|minus|pow|remainder|negate|abs|min|max|movePointLeft|movePointRight"
                    + "|scaleByPowerOfTen|setScale|round|stripTrailingZeros|ulp|sqrt)\\s*\\(");

    private static final Pattern VALIDATE_CALL = Pattern.compile("\\bvalidate\\s*\\(");

    @Test
    @DisplayName("production sources have no validate(, throw, or BigDecimal arithmetic")
    void productionSourcesStayFreeOfLogic() throws IOException {
        Path dir = pricingSources();
        try (Stream<Path> listed = Files.list(dir)) {
            assertThat(listed.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrderElementsOf(PRODUCTION_FILES);
        }
        for (String name : PRODUCTION_FILES) {
            String text = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
            assertThat(VALIDATE_CALL.matcher(text).find())
                    .as("%s contains validate(", name)
                    .isFalse();
            assertThat(text)
                    .as("%s contains throw", name)
                    .doesNotContain("throw");
            assertThat(BIG_DECIMAL_ARITHMETIC.matcher(text).find())
                    .as("%s contains BigDecimal arithmetic", name)
                    .isFalse();
            assertThat(text)
                    .as("%s contains an expand method", name)
                    .doesNotContain("expand(");
        }
    }

    @Test
    @DisplayName("RampStep fields are the ADR-023 contract and nothing else")
    void rampStepFields() {
        Map<String, Class<?>> expected = new LinkedHashMap<>();
        expected.put("sequence", Integer.class);
        expected.put("startMonth", Integer.class);
        expected.put("months", Integer.class);
        expected.put("quantity", Integer.class);
        expected.put("priceBasis", RampPriceBasis.class);
        expected.put("unitPrice", BigDecimal.class);
        expected.put("discountFraction", BigDecimal.class);
        assertFields(RampStep.class, expected);
        assertThat(RampPriceBasis.values()).containsExactly(
                RampPriceBasis.ABSOLUTE,
                RampPriceBasis.DISCOUNT_FRACTION);
    }

    @Test
    @DisplayName("RampEscalator stores inputs and has no expand method")
    void rampEscalatorFields() {
        Map<String, Class<?>> expected = new LinkedHashMap<>();
        expected.put("startPrice", BigDecimal.class);
        expected.put("mode", RampEscalator.Mode.class);
        expected.put("amount", BigDecimal.class);
        expected.put("everyMonths", Integer.class);
        assertFields(RampEscalator.class, expected);
        assertThat(RampEscalator.Mode.values()).containsExactly(
                RampEscalator.Mode.PERCENT,
                RampEscalator.Mode.AMOUNT);
        assertNoExpand(RampStep.class);
        assertNoExpand(RampPriceBasis.class);
        assertNoExpand(RampEscalator.class);
        assertNoExpand(RampEscalator.Mode.class);
    }

    @Test
    @DisplayName("both price fields round-trip; the type accepts either one alone")
    void jsonRoundTrip() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        RampStep absolute = RampStep.builder()
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(50)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(new BigDecimal("40.0000"))
                .build();
        RampStep readAbsolute = mapper.readValue(mapper.writeValueAsString(absolute), RampStep.class);
        assertThat(readAbsolute).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(absolute);
        assertThat(mapper.writeValueAsString(absolute)).doesNotContain("discountFraction");

        RampStep discounted = new RampStep(
                2, 13, 12, 100, RampPriceBasis.DISCOUNT_FRACTION, null, new BigDecimal("0.07"));
        RampStep readDiscounted = mapper.readValue(mapper.writeValueAsString(discounted), RampStep.class);
        assertThat(readDiscounted.getPriceBasis()).isEqualTo(RampPriceBasis.DISCOUNT_FRACTION);
        assertThat(readDiscounted.getDiscountFraction()).isEqualByComparingTo("0.07");
        assertThat(readDiscounted.getUnitPrice()).isNull();

        RampStep both = new RampStep(
                3, 25, 12, 150, RampPriceBasis.ABSOLUTE, new BigDecimal("50"), new BigDecimal("0.10"));
        RampStep readBoth = mapper.readValue(mapper.writeValueAsString(both), RampStep.class);
        assertThat(readBoth.getUnitPrice()).isEqualByComparingTo("50");
        assertThat(readBoth.getDiscountFraction()).isEqualByComparingTo("0.10");

        RampEscalator escalator = RampEscalator.builder()
                .startPrice(new BigDecimal("40"))
                .mode(RampEscalator.Mode.PERCENT)
                .amount(new BigDecimal("0.05"))
                .everyMonths(12)
                .build();
        RampEscalator readEscalator = mapper.readValue(mapper.writeValueAsString(escalator), RampEscalator.class);
        assertThat(readEscalator).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(escalator);
        assertThat(readEscalator.getMode()).isEqualTo(RampEscalator.Mode.PERCENT);
    }

    private static void assertFields(Class<?> type, Map<String, Class<?>> expected) {
        Map<String, Class<?>> actual = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            actual.put(field.getName(), field.getType());
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private static void assertNoExpand(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertThat(method.getName()).isNotEqualTo("expand");
        }
    }

    private static Path pricingSources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path[] candidates = {
                    dir.resolve("src/main/java/com/opencrm/common/pricing"),
                    dir.resolve("libs/common/src/main/java/com/opencrm/common/pricing"),
                    dir.resolve("backend/libs/common/src/main/java/com/opencrm/common/pricing")
            };
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate.resolve("RampStep.java"))) {
                    return candidate;
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "RampStep.java not found above " + Path.of("").toAbsolutePath());
    }
}
