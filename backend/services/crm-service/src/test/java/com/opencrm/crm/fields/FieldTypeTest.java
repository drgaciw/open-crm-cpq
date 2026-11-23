package com.opencrm.crm.fields;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldTypeTest {

    @Test
    void storedAndFromStoredRoundTripEveryConstant() {
        for (FieldType type : FieldType.values()) {
            assertThat(type.stored()).isEqualTo(type.name().toLowerCase(Locale.ROOT));
            assertThat(FieldType.fromStored(type.stored())).isSameAs(type);
        }
    }

    @Test
    void nullAndUnknownValuesAreRejected() {
        assertThatThrownBy(() -> FieldType.fromStored(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field type is required");
        assertThatThrownBy(() -> FieldType.fromStored("not-a-type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown field type: not-a-type");
    }
}
