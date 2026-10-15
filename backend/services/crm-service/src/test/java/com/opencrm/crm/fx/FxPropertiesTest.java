package com.opencrm.crm.fx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FxPropertiesTest {

    @Test
    void defaultsToUsd() {
        assertThat(new FxProperties(null).baseCurrency()).isEqualTo("USD");
        assertThat(new FxProperties("  ").baseCurrency()).isEqualTo("USD");
    }

    @Test
    void normalizesCase() {
        assertThat(new FxProperties(" eur ").baseCurrency()).isEqualTo("EUR");
    }

    @Test
    void rejectsMalformedCode() {
        assertThatThrownBy(() -> new FxProperties("EURO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FxProperties("U5D")).isInstanceOf(IllegalArgumentException.class);
    }
}
