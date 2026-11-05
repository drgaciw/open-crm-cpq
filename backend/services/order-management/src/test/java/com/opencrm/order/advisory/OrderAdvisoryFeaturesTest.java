package com.opencrm.order.advisory;

import com.opencrm.jev.state.JevState;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static com.opencrm.order.advisory.OrderAdvisoryFeatures.*;
import static org.assertj.core.api.Assertions.*;

class OrderAdvisoryFeaturesTest {
    @Test
    void missingFactsRemainUnknownInsteadOfFalse() {
        assertThat(state(null).values()).hasSize(8).allSatisfy((key, value) -> assertThat(value).isEqualTo("UNKNOWN"));
        assertThat(Flag.of(null)).isEqualTo(Flag.UNKNOWN);
        assertThat(Flag.of(false)).isEqualTo(Flag.NO);
        assertThat(Flag.of(true)).isEqualTo(Flag.YES);
    }

    @Test
    void rawCodesAndIdentifiersCannotBecomeFeatures() {
        for (String untrusted : Arrays.asList(null, "customer-123", "alice@example.com", "Sensitive reason", "HIGH ", "NEW_CATEGORY", "x".repeat(1000))) {
            assertThat(Category.from(untrusted)).isEqualTo(Category.UNKNOWN);
            assertThat(Severity.from(untrusted)).isEqualTo(Severity.UNKNOWN);
            assertThat(state(new Facts(Category.from(untrusted), Severity.from(untrusted), null, null, null, null, null, null)).values())
                    .allSatisfy((key, value) -> assertThat(value).isEqualTo("UNKNOWN"));
        }
        assertThat(Facts.class.getRecordComponents()).allSatisfy(component -> assertThat(component.getType().isEnum()).isTrue());
        assertThat(SCHEMA.features().keySet()).isEqualTo(Set.of("category", "severity", "ageBand", "unknownOutcome", "pendingEvidence", "conflictingEvidence", "activationPending", "financePending"));
    }

    @Test
    void durationBandsHaveExplicitBoundariesAndNoOverflow() {
        assertThat(AgeBand.from(null)).isEqualTo(AgeBand.UNKNOWN);
        assertThat(AgeBand.from(Duration.ofNanos(-1))).isEqualTo(AgeBand.UNKNOWN);
        assertThat(AgeBand.from(Duration.ZERO)).isEqualTo(AgeBand.UNDER_HOUR);
        assertThat(AgeBand.from(Duration.ofHours(1).minusNanos(1))).isEqualTo(AgeBand.UNDER_HOUR);
        assertThat(AgeBand.from(Duration.ofHours(1))).isEqualTo(AgeBand.HOURS_1_TO_24);
        assertThat(AgeBand.from(Duration.ofDays(1).minusNanos(1))).isEqualTo(AgeBand.HOURS_1_TO_24);
        assertThat(AgeBand.from(Duration.ofDays(1))).isEqualTo(AgeBand.DAYS_1_TO_7);
        assertThat(AgeBand.from(Duration.ofDays(7).minusNanos(1))).isEqualTo(AgeBand.DAYS_1_TO_7);
        assertThat(AgeBand.from(Duration.ofDays(7))).isEqualTo(AgeBand.DAYS_7_PLUS);
        assertThat(AgeBand.from(Duration.ofSeconds(Long.MAX_VALUE))).isEqualTo(AgeBand.DAYS_7_PLUS);
    }

    @Test
    void hashesAreDeterministicAndChangeWithMissingKnowledge() {
        var facts = new Facts(Category.UNKNOWN_OUTCOME, Severity.HIGH, AgeBand.HOURS_1_TO_24, Flag.YES, Flag.NO, Flag.UNKNOWN, Flag.YES, Flag.NO);
        var original = state(facts);
        var reversed = JevState.builder(SCHEMA);
        var keys = original.values().keySet().stream().sorted(java.util.Comparator.reverseOrder()).toList();
        keys.forEach(key -> reversed.put(key, original.values().get(key)));
        assertThat(reversed.build().sha256()).isEqualTo(original.sha256()).hasSize(64);
        assertThat(state(facts).canonicalJson()).isEqualTo(original.canonicalJson());
        assertThat(state(new Facts(Category.UNKNOWN_OUTCOME, Severity.HIGH, AgeBand.HOURS_1_TO_24, Flag.YES, Flag.NO, Flag.NO, Flag.YES, Flag.NO)).sha256())
                .isNotEqualTo(original.sha256());
    }

    @Test
    void allCategoryInputsProduceOnlyBoundedEnumCodes() {
        for (Category category : Category.values()) {
            assertThat(Category.from(category.name())).isSameAs(category);
            var result = state(new Facts(category, Severity.CRITICAL, AgeBand.DAYS_7_PLUS, Flag.UNKNOWN, Flag.YES, Flag.NO, Flag.UNKNOWN, Flag.YES));
            assertThat(result.values()).allSatisfy((key, value) -> assertThat(value.toString()).matches("[A-Z0-9_]{1,64}"));
            assertThat(result.canonicalJson().length()).isLessThan(400);
        }
    }
}
