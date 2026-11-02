package com.opencrm.crm.buyingrole;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyingRoleTest {

    @Test
    @DisplayName("the catalog is the six closed committee roles")
    void closedCatalog() {
        assertThat(BuyingRole.values()).containsExactly(
                BuyingRole.DECISION_MAKER,
                BuyingRole.CHAMPION,
                BuyingRole.ECONOMIC_BUYER,
                BuyingRole.BILLING_CONTACT,
                BuyingRole.TECHNICAL_EVALUATOR,
                BuyingRole.BLOCKER);
    }

    @Test
    @DisplayName("each role round-trips through the exhaustive storage switch")
    void storageRoundTrip() {
        BuyingRoleConverter converter = new BuyingRoleConverter();
        for (BuyingRole role : BuyingRole.values()) {
            assertThat(role.storageValue()).isEqualTo(role.name());
            assertThat(BuyingRole.fromStorage(role.storageValue())).isSameAs(role);
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(role))).isSameAs(role);
        }
    }

    @Test
    @DisplayName("null storage stays null")
    void nullStorage() {
        BuyingRoleConverter converter = new BuyingRoleConverter();
        assertThat(BuyingRole.fromStorage(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("free text and lookalikes are rejected")
    void rejectsFreeText() {
        assertThatThrownBy(() -> BuyingRole.fromStorage("decision maker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown buying role");
        assertThatThrownBy(() -> BuyingRole.fromStorage("DECISION_MAKER "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuyingRoleConverter().convertToEntityAttribute("champion"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown buying role");
    }
}
