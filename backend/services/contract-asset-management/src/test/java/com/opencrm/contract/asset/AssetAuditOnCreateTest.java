package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exactly one audit outbox publication per asset, including when activation is replayed. */
class AssetAuditOnCreateTest {

    @Test
    void oneAuditEntryPerAssetIncludingReplay() throws Exception {
        var event = AssetActivationTestSupport.twoLines(AssetActivationTestSupport.fixture());
        AssetActivationTestSupport world = AssetActivationTestSupport.open(new EmptyEntitlementLookup());

        world.subscriptions.apply(event);
        world.subscriptions.apply(event);
        world.activation.onActivated(event, world.subscription, world.periods);

        assertThat(world.assets).hasSize(2);
        assertThat(world.auditCount()).isEqualTo(2);
        assertThat(world.createdEvents()).hasSize(2);
    }
}
