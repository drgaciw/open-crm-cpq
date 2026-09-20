package com.opencrm.jev.question;

import java.util.List;
import java.util.Map;

public final class JevQuestions {

    private JevQuestions() {
    }

    /** @param criteria option key to description; pass an insertion-ordered map */
    public static JevQuestion.Choice choice(String id, String instructions, Map<String, String> criteria) {
        return new JevQuestion.Choice(id, instructions, criteria);
    }

    public static JevQuestion.Noul noul(String id, String instructions) {
        return new JevQuestion.Noul(id, instructions, null, null);
    }

    public static JevQuestion.Noul noul(String id, String instructions, String whenTrue, String whenFalse) {
        return new JevQuestion.Noul(id, instructions, whenTrue, whenFalse);
    }

    /** @param criteria ascending rubric; index 0 is the lowest level */
    public static JevQuestion.Score score(String id, String instructions, List<String> criteria) {
        return new JevQuestion.Score(id, instructions, criteria);
    }
}
