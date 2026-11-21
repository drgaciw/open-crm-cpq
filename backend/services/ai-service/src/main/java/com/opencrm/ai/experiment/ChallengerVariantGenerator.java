package com.opencrm.ai.experiment;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates challenger DRL variants by scaling the first decimal literal
 * (typical discount / rate) found in the control content.
 *
 * <p>No MCP / RL parameter extractor — Wave 3 keeps generation local and deterministic.</p>
 */
@Component
public class ChallengerVariantGenerator {

    private static final Pattern DECIMAL = Pattern.compile("(?<!\\w)(0\\.\\d+|1\\.0+|\\d+\\.\\d+)(?!\\w)");
    private static final double[] DEFAULT_FACTORS = {0.85, 1.15, 0.70, 1.30};

    public List<GeneratedVariant> generate(String controlContent, int challengerCount) {
        if (controlContent == null || controlContent.isBlank()) {
            throw new IllegalArgumentException("Control rule content is blank; cannot generate challengers");
        }
        int count = Math.max(1, Math.min(10, challengerCount));
        List<GeneratedVariant> variants = new ArrayList<>(count);
        Matcher matcher = DECIMAL.matcher(controlContent);
        if (!matcher.find()) {
            for (int i = 0; i < count; i++) {
                variants.add(new GeneratedVariant(
                        "challenger-" + (i + 1),
                        "Clone of control (no numeric literal found to mutate)",
                        controlContent));
            }
            return variants;
        }

        String matched = matcher.group(1);
        double original = Double.parseDouble(matched);
        int start = matcher.start(1);
        int end = matcher.end(1);

        for (int i = 0; i < count; i++) {
            double factor = DEFAULT_FACTORS[i % DEFAULT_FACTORS.length];
            double mutated = clampRate(original * factor);
            String replacement = formatDecimal(mutated);
            String content = controlContent.substring(0, start) + replacement + controlContent.substring(end);
            variants.add(new GeneratedVariant(
                    "challenger-" + (i + 1),
                    "Scaled first numeric literal " + matched + " → " + replacement + " (×" + factor + ")",
                    content));
        }
        return variants;
    }

    static double clampRate(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1.0 && value <= 100) {
            // Likely a percent literal already > 1; keep within 0–100
            return Math.min(100, value);
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    public record GeneratedVariant(String name, String description, String ruleContent) {
    }
}
