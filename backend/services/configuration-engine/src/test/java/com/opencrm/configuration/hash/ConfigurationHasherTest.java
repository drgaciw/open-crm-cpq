package com.opencrm.configuration.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DRG-428 / BC-02-05 AC: "Hash is deterministic over selections, catalog version and rule set version."
 */
@DisplayName("ConfigurationHasher (DRG-428 / BC-02-05 ComputeHash)")
class ConfigurationHasherTest {

    @Test
    @DisplayName("same inputs always produce the same hash, computed twice")
    void sameInputsProduceTheSameHashTwice() {
        Map<String, String> selections = Map.of("color", "red", "size", "L");

        String first = ConfigurationHasher.compute(selections, "2026-Q4", "schema-v3");
        String second = ConfigurationHasher.compute(selections, "2026-Q4", "schema-v3");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}"); // lowercase hex SHA-256
    }

    @Test
    @DisplayName("selection insertion order never affects the hash")
    void selectionOrderDoesNotMatter() {
        Map<String, String> insertedThisOrder = new LinkedHashMap<>();
        insertedThisOrder.put("color", "red");
        insertedThisOrder.put("size", "L");
        insertedThisOrder.put("warranty", "extended");

        Map<String, String> insertedReverseOrder = new LinkedHashMap<>();
        insertedReverseOrder.put("warranty", "extended");
        insertedReverseOrder.put("size", "L");
        insertedReverseOrder.put("color", "red");

        Map<String, String> insertedViaTreeMap = new TreeMap<>(insertedThisOrder);

        String hashA = ConfigurationHasher.compute(insertedThisOrder, "catalog-1", "rules-1");
        String hashB = ConfigurationHasher.compute(insertedReverseOrder, "catalog-1", "rules-1");
        String hashC = ConfigurationHasher.compute(insertedViaTreeMap, "catalog-1", "rules-1");

        assertThat(hashA).isEqualTo(hashB).isEqualTo(hashC);
    }

    @Test
    @DisplayName("a different selection value changes the hash")
    void differentSelectionsProduceDifferentHashes() {
        String baseline = ConfigurationHasher.compute(Map.of("color", "red"), "catalog-1", "rules-1");
        String changed = ConfigurationHasher.compute(Map.of("color", "blue"), "catalog-1", "rules-1");

        assertThat(baseline).isNotEqualTo(changed);
    }

    @Test
    @DisplayName("a different catalog version changes the hash even with identical selections")
    void differentCatalogVersionProducesDifferentHash() {
        Map<String, String> selections = Map.of("color", "red");

        String catalogA = ConfigurationHasher.compute(selections, "catalog-A", "rules-1");
        String catalogB = ConfigurationHasher.compute(selections, "catalog-B", "rules-1");

        assertThat(catalogA).isNotEqualTo(catalogB);
    }

    @Test
    @DisplayName("a different rule set version changes the hash even with identical selections")
    void differentRuleSetVersionProducesDifferentHash() {
        Map<String, String> selections = Map.of("color", "red");

        String rulesA = ConfigurationHasher.compute(selections, "catalog-1", "rules-A");
        String rulesB = ConfigurationHasher.compute(selections, "catalog-1", "rules-B");

        assertThat(rulesA).isNotEqualTo(rulesB);
    }

    @Test
    @DisplayName("a null catalog version is normalized to empty, not treated as a distinct marker value")
    void nullCatalogVersionIsNormalizedConsistently() {
        Map<String, String> selections = Map.of("color", "red");

        String withNull = ConfigurationHasher.compute(selections, null, "rules-1");
        String withEmpty = ConfigurationHasher.compute(selections, "", "rules-1");

        assertThat(withNull).isEqualTo(withEmpty);
    }

    @Test
    @DisplayName("an empty selection map is handled without error and still mixes in catalog/rule-set version")
    void emptySelectionsStillHash() {
        String hash = ConfigurationHasher.compute(Map.of(), "catalog-1", "rules-1");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("selections and rule set version must not be null")
    void requiredInputsAreValidated() {
        assertThatThrownBy(() -> ConfigurationHasher.compute(null, "catalog-1", "rules-1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ConfigurationHasher.compute(Map.of(), "catalog-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a selection value containing JSON-special characters does not collide with an unescaped one")
    void specialCharactersDoNotCauseCollisions() {
        String plain = ConfigurationHasher.compute(Map.of("note", "a,b"), "catalog-1", "rules-1");
        String escaped = ConfigurationHasher.compute(Map.of("note", "a\",\"b"), "catalog-1", "rules-1");

        assertThat(plain).isNotEqualTo(escaped);
    }
}
