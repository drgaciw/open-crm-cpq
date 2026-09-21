package com.opencrm.jev.answer;

import java.util.Collection;

public final class JevConfidence {

    private JevConfidence() {
    }

    /** The weakest answer bounds the batch: minimum, not product. Empty means no confidence. */
    public static double aggregate(Collection<? extends JevAnswer> answers) {
        return answers.stream().mapToDouble(JevAnswer::confidence).min().orElse(0);
    }
}
