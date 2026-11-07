package com.opencrm.contract.service;

import com.opencrm.contract.domain.Subscription;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-663 AC2: entity maps nullable subscriptions.customer_account_id. */
@Tag("unit")
class SubscriptionCustomerAccountMappingTest {

    @Test
    void customerAccountIdMappedAsNullableUuidColumn() throws Exception {
        Field field = Subscription.class.getDeclaredField("customerAccountId");
        assertThat(field.getType()).isEqualTo(UUID.class);

        Column column = field.getAnnotation(Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("customer_account_id");
        assertThat(column.nullable()).isTrue();
    }
}
