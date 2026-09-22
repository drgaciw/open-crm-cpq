package com.opencrm.jev.shadow;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Summarises how shadow answers compare with authoritative outcomes: the evidence ADR-015 requires
 * before a suggestion is trusted. Works on choice-style outcomes (tier, category).
 *
 * @param confusion authoritative outcome to (Jev answer to count)
 * @param expectedCalibrationError mean gap between stated confidence and observed accuracy, 10 bins
 */
public record ShadowAgreementReport(
        int compared,
        double agreementRate,
        Map<String, Map<String, Integer>> confusion,
        double expectedCalibrationError) {

    /** One comparable observation. */
    public record Observation(String authoritative, String answer, double confidence) {
    }

    private static final int BINS = 10;

    public static ShadowAgreementReport of(List<Observation> observations) {
        if (observations.isEmpty()) {
            return new ShadowAgreementReport(0, 0, Map.of(), 0);
        }
        Map<String, Map<String, Integer>> confusion = new TreeMap<>();
        int agreed = 0;
        int[] binCount = new int[BINS];
        int[] binCorrect = new int[BINS];
        double[] binConfidence = new double[BINS];
        for (Observation o : observations) {
            confusion.computeIfAbsent(o.authoritative(), k -> new TreeMap<>()).merge(o.answer(), 1, Integer::sum);
            boolean correct = o.authoritative().equals(o.answer());
            if (correct) {
                agreed++;
            }
            int bin = Math.min(BINS - 1, (int) (o.confidence() * BINS));
            binCount[bin]++;
            binConfidence[bin] += o.confidence();
            if (correct) {
                binCorrect[bin]++;
            }
        }
        double ece = 0;
        for (int i = 0; i < BINS; i++) {
            if (binCount[i] > 0) {
                double accuracy = (double) binCorrect[i] / binCount[i];
                double meanConfidence = binConfidence[i] / binCount[i];
                ece += Math.abs(accuracy - meanConfidence) * binCount[i] / observations.size();
            }
        }
        return new ShadowAgreementReport(observations.size(), (double) agreed / observations.size(),
                confusion, ece);
    }
}
