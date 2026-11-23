package com.opencrm.crm.fields;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEntityKindTest {

    @Test
    void storedAndFromStoredRoundTripEveryConstant() {
        for (FieldEntityKind kind : FieldEntityKind.values()) {
            assertThat(kind.stored()).isEqualTo(kind.name().toLowerCase(Locale.ROOT));
            assertThat(FieldEntityKind.fromStored(kind.stored())).isSameAs(kind);
        }
    }

    @Test
    void nullAndUnknownValuesAreRejected() {
        assertThatThrownBy(() -> FieldEntityKind.fromStored(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field entity is required");
        assertThatThrownBy(() -> FieldEntityKind.fromStored("lead"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown field entity: lead");
    }
}
