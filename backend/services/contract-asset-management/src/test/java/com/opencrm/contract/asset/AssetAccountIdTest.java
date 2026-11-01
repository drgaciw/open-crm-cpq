package com.opencrm.contract.asset;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** accountId is the accepted-quote customer id. There is no second customer column. */
class AssetAccountIdTest {

    @Test
    void assetAccountIdEqualsAcceptedCustomerId() throws Exception {
        var event = AssetActivationTestSupport.fixture();
        assertThat(event.getCustomer().getCustomerId()).isEqualTo(event.getCustomerId());
        AssetActivationTestSupport world = AssetActivationTestSupport.open(new EmptyEntitlementLookup());

        world.subscriptions.apply(event);

        assertThat(world.assets.values()).singleElement().satisfies(asset ->
                assertThat(asset.getAccountId()).isEqualTo(event.getCustomerId()));
        assertThat(world.createdEvents()).singleElement().satisfies(created ->
                assertThat(created.accountId()).isEqualTo(event.getCustomerId()));
        assertThat(Arrays.stream(Asset.class.getDeclaredFields()).map(Field::getName))
                .contains("accountId")
                .doesNotContain("customerId");
    }
}
