package com.opencrm.crm.guided;

import com.opencrm.common.exception.BusinessException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic needs-to-product map (DRG-487). No model call and no second constraint solver.
 * The configuration engine still validates the pre-selected options.
 *
 * <p>Precedence, first match wins:
 * <ol>
 *   <li>priority {@code compliance} or deployment {@code on-prem} → governed suite</li>
 *   <li>priority {@code cost} or team size {@code small} → starter, cloud deployment</li>
 *   <li>team size {@code enterprise} → growth platform, enterprise edition</li>
 *   <li>anything else, including no answers → growth platform, professional edition</li>
 * </ol>
 */
public final class NeedsSolutionDerivation {

    public static final String QUESTION_DEPLOYMENT = "deployment";
    public static final String QUESTION_TEAM_SIZE = "team-size";
    public static final String QUESTION_PRIORITY = "priority";

    public static final String PRODUCT_STARTER = "prod-starter";
    public static final String PRODUCT_GROWTH = "prod-growth-platform";
    public static final String PRODUCT_GOVERNED = "prod-governed-suite";

    public static final String CONSTRAINT_NOTE =
            "Advisory recommendation subject to configuration constraints. "
                    + "Derivation does not complete the configuration session and does not mark a deal won.";

    private static final Map<String, Question> QUESTIONS = questions();

    private NeedsSolutionDerivation() {
    }

    public static List<Question> catalog() {
        return List.copyOf(QUESTIONS.values());
    }

    public static void requireKnown(String questionKey, String answer) {
        Question question = QUESTIONS.get(questionKey);
        if (question == null) {
            throw new BusinessException("Unknown guided-selling question", "GUIDED_QUESTION_UNKNOWN");
        }
        if (answer == null || !question.allowedValues().contains(answer)) {
            throw new BusinessException(
                    "Answer for " + question.key() + " must be one of " + question.allowedValues(),
                    "GUIDED_ANSWER_UNKNOWN");
        }
    }

    public static DerivedNeeds derive(Map<String, String> answers) {
        Map<String, String> known = knownAnswers(answers);
        String deployment = known.get(QUESTION_DEPLOYMENT);
        String teamSize = known.get(QUESTION_TEAM_SIZE);
        String priority = known.get(QUESTION_PRIORITY);

        if ("compliance".equals(priority) || "on-prem".equals(deployment)) {
            return governed(deployment);
        }
        if ("cost".equals(priority) || "small".equals(teamSize)) {
            return starter();
        }
        if ("enterprise".equals(teamSize)) {
            return growth("enterprise", "premium", deployment,
                    "An enterprise team maps to the growth platform with premium support.");
        }
        return growth("professional", "standard", deployment,
                "These needs map to the growth platform, professional edition.");
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static DerivedNeeds governed(String deployment) {
        Map<String, String> selections = new LinkedHashMap<>();
        selections.put("edition", "enterprise");
        selections.put("deployment", "on-prem".equals(deployment) ? "on-prem" : "private-cloud");
        selections.put("support", "premium");
        selections.put("data-residency", "required");
        return new DerivedNeeds(PRODUCT_GOVERNED, freeze(selections),
                "Compliance or on-premises deployment maps to the governed suite. "
                        + "Options stay advisory until the configuration session validates them.");
    }

    private static DerivedNeeds starter() {
        Map<String, String> selections = new LinkedHashMap<>();
        selections.put("edition", "standard");
        selections.put("deployment", "cloud");
        selections.put("support", "community");
        return new DerivedNeeds(PRODUCT_STARTER, freeze(selections),
                "Cost or a small team maps to the starter product on cloud.");
    }

    private static DerivedNeeds growth(String edition, String support, String deployment, String rationale) {
        Map<String, String> selections = new LinkedHashMap<>();
        selections.put("edition", edition);
        selections.put("deployment", deploymentOrCloud(deployment));
        selections.put("support", support);
        return new DerivedNeeds(PRODUCT_GROWTH, freeze(selections), rationale);
    }

    private static String deploymentOrCloud(String deployment) {
        if ("cloud".equals(deployment) || "hybrid".equals(deployment) || "on-prem".equals(deployment)) {
            return deployment;
        }
        return "cloud";
    }

    private static Map<String, String> knownAnswers(Map<String, String> answers) {
        Map<String, String> known = new LinkedHashMap<>();
        if (answers == null) {
            return known;
        }
        answers.forEach((key, value) -> {
            String normalizedKey = normalize(key);
            String normalizedValue = normalize(value);
            Question question = QUESTIONS.get(normalizedKey);
            if (question != null && question.allowedValues().contains(normalizedValue)) {
                known.put(normalizedKey, normalizedValue);
            }
        });
        return known;
    }

    private static Map<String, Question> questions() {
        Map<String, Question> catalog = new LinkedHashMap<>();
        catalog.put(QUESTION_DEPLOYMENT, new Question(QUESTION_DEPLOYMENT, "Where will this run?",
                List.of("cloud", "on-prem", "hybrid")));
        catalog.put(QUESTION_TEAM_SIZE, new Question(QUESTION_TEAM_SIZE, "How large is the team?",
                List.of("small", "mid", "enterprise")));
        catalog.put(QUESTION_PRIORITY, new Question(QUESTION_PRIORITY, "What matters most?",
                List.of("speed", "compliance", "cost")));
        return Collections.unmodifiableMap(catalog);
    }

    private static Map<String, String> freeze(Map<String, String> selections) {
        return Collections.unmodifiableMap(selections);
    }

    public record Question(String key, String prompt, List<String> allowedValues) {
    }

    public record DerivedNeeds(String productId, Map<String, String> selections, String rationale) {
    }
}
