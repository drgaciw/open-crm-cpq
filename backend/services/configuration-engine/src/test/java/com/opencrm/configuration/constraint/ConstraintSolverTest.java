package com.opencrm.configuration.constraint;

import com.opencrm.configuration.session.ConfigurationSession;
import com.opencrm.configuration.session.ConfigurationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Configuration Engine - Constraint Solver")
class ConstraintSolverTest {

    private final ConstraintSolver constraintSolver = new ConstraintSolver();

    @Test
    @DisplayName("should reject recommendation candidate that violates mutual exclusivity")
    void rejectsIncompatibleRecommendationCandidate() {
        ConfigurationSession session = sessionWithSelections(Map.of(
                "color", "red",
                "option_a", "selected"));

        boolean compatible = constraintSolver.isCompatible(session, "option_b", "selected");

        assertFalse(compatible);
    }

    @Test
    @DisplayName("should reject recommendation candidate that violates dependency boundary")
    void rejectsRecommendationCandidateMissingDependency() {
        ConfigurationSession session = sessionWithSelections(Map.of("color", "red"));

        boolean compatible = constraintSolver.isCompatible(session, "premium_feature", "yes");

        assertFalse(compatible);
    }

    @Test
    @DisplayName("should accept recommendation candidate when validation boundaries remain satisfied")
    void acceptsValidRecommendationCandidate() {
        ConfigurationSession session = sessionWithSelections(Map.of(
                "color", "red",
                "base_package", "standard"));

        boolean compatible = constraintSolver.isCompatible(session, "premium_feature", "yes");

        assertTrue(compatible);
    }

    private ConfigurationSession sessionWithSelections(Map<String, String> selections) {
        return ConfigurationSession.builder()
                .productId("PROD-001")
                .userId("user-1")
                .status(ConfigurationStatus.IN_PROGRESS)
                .selections(selections)
                .build();
    }
}
