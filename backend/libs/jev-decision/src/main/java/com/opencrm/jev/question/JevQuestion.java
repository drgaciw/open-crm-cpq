package com.opencrm.jev.question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A typed question put to Jev. The id keys the question in the request and its answer in the response.
 */
public sealed interface JevQuestion {

    String id();

    String instructions();

    /** Wire value of the {@code type} field. */
    String type();

    /** Pick exactly one key. Criteria order is preserved on the wire. */
    record Choice(String id, String instructions, Map<String, String> criteria) implements JevQuestion {
        public Choice {
            requireText(id, "id");
            requireText(instructions, "instructions");
            if (criteria == null || criteria.size() < 2) {
                throw new IllegalArgumentException("choice question " + id + " needs at least two criteria");
            }
            criteria = Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
        }

        @Override
        public String type() {
            return "choice";
        }
    }

    /** Probability that a statement is true. Criteria are optional. */
    record Noul(String id, String instructions, String trueCriterion, String falseCriterion)
            implements JevQuestion {
        public Noul {
            requireText(id, "id");
            requireText(instructions, "instructions");
        }

        @Override
        public String type() {
            return "noul";
        }
    }

    /** Position on an ordered rubric; criteria ascend from index 0. */
    record Score(String id, String instructions, List<String> criteria) implements JevQuestion {
        public Score {
            requireText(id, "id");
            requireText(instructions, "instructions");
            if (criteria == null || criteria.size() < 2) {
                throw new IllegalArgumentException("score question " + id + " needs at least two criteria");
            }
            criteria = List.copyOf(criteria);
        }

        @Override
        public String type() {
            return "score";
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
