package com.opencrm.jev.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JevState allow-list (ADR-015 data minimisation)")
class JevStateAllowListTest {

    private enum Source { WEB, REFERRAL }

    private static final JevFeatureSchema SCHEMA = JevFeatureSchema.named("lead-score", 1)
            .code("source")
            .number("discount_pct")
            .integer("days_since_created")
            .bool("has_contact")
            .build();

    @Test
    @DisplayName("rejects a feature the schema does not declare")
    void rejectsUndeclaredFeature() {
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("email", "a@b.example"))
                .isInstanceOf(FeatureNotAllowedException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("rejects free text offered as a code")
    void rejectsFreeText() {
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("source", "Met Jane Doe at the expo"))
                .isInstanceOf(FeatureNotAllowedException.class);
    }

    @Test
    @DisplayName("rejects a code longer than 64 characters")
    void rejectsLongCode() {
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("source", "x".repeat(65)))
                .isInstanceOf(FeatureNotAllowedException.class);
    }

    @Test
    @DisplayName("rejects a value of the wrong type")
    void rejectsWrongType() {
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("has_contact", "yes"))
                .isInstanceOf(FeatureNotAllowedException.class);
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("days_since_created", 1.5))
                .isInstanceOf(FeatureNotAllowedException.class);
        assertThatThrownBy(() -> JevState.builder(SCHEMA).put("discount_pct", Double.NaN))
                .isInstanceOf(FeatureNotAllowedException.class);
    }

    @Test
    @DisplayName("accepts enums as codes and keeps null as an explicit unknown")
    void acceptsEnumAndNull() {
        JevState state = JevState.builder(SCHEMA)
                .put("source", Source.REFERRAL)
                .put("days_since_created", null)
                .build();

        assertThat(state.values()).containsEntry("source", "REFERRAL").containsEntry("days_since_created", null);
    }

    @Test
    @DisplayName("canonical JSON and hash do not depend on insertion order")
    void canonicalFormIsOrderIndependent() {
        JevState first = JevState.builder(SCHEMA).put("source", "WEB").put("has_contact", true).build();
        JevState second = JevState.builder(SCHEMA).put("has_contact", true).put("source", "WEB").build();

        assertThat(first.canonicalJson()).isEqualTo("{\"has_contact\":true,\"source\":\"WEB\"}");
        assertThat(first.sha256()).isEqualTo(second.sha256()).hasSize(64);
    }

    @Test
    @DisplayName("hash changes with the schema version")
    void hashDependsOnSchemaVersion() {
        JevFeatureSchema v2 = JevFeatureSchema.named("lead-score", 2).code("source").build();
        JevFeatureSchema v1 = JevFeatureSchema.named("lead-score", 1).code("source").build();

        assertThat(JevState.builder(v1).put("source", "WEB").build().sha256())
                .isNotEqualTo(JevState.builder(v2).put("source", "WEB").build().sha256());
    }
}
