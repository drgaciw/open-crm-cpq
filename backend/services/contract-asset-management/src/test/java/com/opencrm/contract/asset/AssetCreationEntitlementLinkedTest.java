package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** When a lookup can name an entitlement, the asset and AssetCreated carry that id. */
class AssetCreationEntitlementLinkedTest {

    @Test
    void activationLinksEntitlementIdWhenLookupReturnsOne() throws Exception {
        var event = AssetActivationTestSupport.fixture();
        AssetActivationTestSupport world = AssetActivationTestSupport.open(
                contractLineId -> Optional.of("ent-drg-700"));

        world.subscriptions.apply(event);

        assertThat(world.assets.values()).singleElement()
                .extracting(Asset::getEntitlementId)
                .isEqualTo("ent-drg-700");
        assertThat(world.createdEvents()).singleElement()
                .extracting(created -> created.entitlementId())
                .isEqualTo("ent-drg-700");
    }
}
