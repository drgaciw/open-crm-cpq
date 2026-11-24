package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-902 AC1 / C19. Every pair of the four installed-base statuses. */
class AssetLifecycleTransitionsTest {

    private final AssetLifecycleTransitions transitions = new AssetLifecycleTransitions();

    @Test
    void everyAllowedAndRefusedPairAmongTheFourStatuses() {
        assertThat(AssetStatus.values()).containsExactly(
                AssetStatus.ACTIVE, AssetStatus.AMENDED, AssetStatus.SUPERSEDED, AssetStatus.TERMINATED);
        Map<AssetStatus, Set<AssetStatus>> allowed = Map.of(
                AssetStatus.ACTIVE, EnumSet.of(AssetStatus.AMENDED, AssetStatus.TERMINATED, AssetStatus.SUPERSEDED),
                AssetStatus.AMENDED, EnumSet.of(AssetStatus.AMENDED, AssetStatus.TERMINATED, AssetStatus.SUPERSEDED),
                AssetStatus.SUPERSEDED, EnumSet.noneOf(AssetStatus.class),
                AssetStatus.TERMINATED, EnumSet.noneOf(AssetStatus.class));

        for (AssetStatus from : AssetStatus.values()) {
            for (AssetStatus to : AssetStatus.values()) {
                assertThat(transitions.allowed(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(allowed.get(from).contains(to));
            }
        }
    }

    @Test
    void nullStatusIsRefused() {
        assertThat(transitions.allowed(null, AssetStatus.ACTIVE)).isFalse();
        assertThat(transitions.allowed(AssetStatus.ACTIVE, null)).isFalse();
    }
}
