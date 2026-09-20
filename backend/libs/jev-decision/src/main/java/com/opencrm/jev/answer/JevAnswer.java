package com.opencrm.jev.answer;

import java.util.List;
import java.util.Map;

/** A validated answer. Instances should only come from {@link JevAnswerValidator}. */
public sealed interface JevAnswer {

    /**
     * How sure the model is, in [0,1]. A noul has no separate confidence: its distance from 0.5 is the
     * signal.
     */
    double confidence();

    record Choice(String choice, double confidence, Map<String, Double> probabilities) implements JevAnswer {
        public Choice {
            probabilities = Map.copyOf(probabilities);
        }
    }

    record Noul(double pTrue) implements JevAnswer {
        @Override
        public double confidence() {
            return Math.abs(pTrue - 0.5) * 2;
        }
    }

    /** @param probabilities one entry per rubric level, index-aligned with the criteria of the question */
    record Score(double score, double confidence, List<Double> probabilities) implements JevAnswer {
        public Score {
            probabilities = List.copyOf(probabilities);
        }

        /** Expected level as a fraction of the top level, in [0,1]. */
        public double normalisedExpectation() {
            int top = probabilities.size() - 1;
            double expected = 0;
            for (int i = 0; i < probabilities.size(); i++) {
                expected += probabilities.get(i) * i;
            }
            return top == 0 ? 0 : expected / top;
        }
    }
}
