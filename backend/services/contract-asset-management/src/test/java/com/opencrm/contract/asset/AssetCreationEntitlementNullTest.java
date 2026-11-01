package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Activation with no entitlement source leaves entitlementId empty and emits AssetCreated. */
class AssetCreationEntitlementNullTest {

    @Test
    void activationWithoutAnEntitlementCreatesOneAssetPerLineWithNullEntitlement() throws Exception {
        var event = AssetActivationTestSupport.fixture();
        AssetActivationTestSupport world = AssetActivationTestSupport.open(new EmptyEntitlementLookup());

        world.subscriptions.apply(event);

        assertThat(world.assets.values()).singleElement().satisfies(asset -> {
            assertThat(asset.getEntitlementId()).isNull();
            assertThat(asset.getContractLineId()).isEqualTo("11111111-1111-4111-8111-000000000201");
            assertThat(asset.getSubscriptionId()).isEqualTo(world.subscription.getId());
        });
        assertThat(world.periods).hasSizeGreaterThan(1);
        assertThat(world.createdEvents()).singleElement().satisfies(created -> {
            assertThat(created.entitlementId()).isNull();
            assertThat(created.contractLineId()).isEqualTo("11111111-1111-4111-8111-000000000201");
            assertThat(created.subscriptionId()).isEqualTo(world.subscription.getId());
        });
        AssetCreatedEvent created = world.createdEvents().getFirst();
        assertThat(created.assetId()).isEqualTo(world.assets.values().iterator().next().getUuid());
    }
}
