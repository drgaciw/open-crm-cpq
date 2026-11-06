package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldRulesTest {

    @Test
    void textEmailAndPhoneShareAStorageClassAndNumberDoesNot() {
        assertThat(FieldType.TEXT.compatibleWith(FieldType.EMAIL)).isTrue();
        assertThat(FieldType.URL.compatibleWith(FieldType.PHONE)).isTrue();
        assertThat(FieldType.LONG_TEXT.storageClass()).isEqualTo(StorageClass.TEXT);
        assertThat(FieldType.TEXT.compatibleWith(FieldType.NUMBER)).isFalse();
        assertThat(FieldType.SELECT.storageClass()).isEqualTo(StorageClass.OPTION);
        assertThat(FieldType.CHECKBOX.storageClass()).isEqualTo(StorageClass.BOOL);
        assertThat(FieldType.USER.storageClass()).isEqualTo(StorageClass.USER);
        assertThat(FieldType.DATE.storageClass()).isEqualTo(StorageClass.DATE);
    }

    @Test
    void keyIsALowercaseIdentifierAndCannotChange() {
        assertThat(FieldRules.requireKey(" region_code ")).isEqualTo("region_code");
        assertThatThrownBy(() -> FieldRules.requireKey("Region")).isInstanceOf(BusinessException.class);
        FieldRules.rejectKeyChange("region_code", "region_code");
        assertThatThrownBy(() -> FieldRules.rejectKeyChange("region_code", "other"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_KEY_IMMUTABLE"));
    }

    @Test
    void standardKeysCannotBeCreatedRetypedOrArchived() {
        assertThatThrownBy(() -> FieldRules.rejectStandardKey(FieldEntityKind.ORGANIZATION, "name"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_KEY_RESERVED"));
        assertThatThrownBy(() -> FieldRules.rejectStandardMutation(true, true, false, false, false, false))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_STANDARD_LOCKED"));
        assertThatThrownBy(() -> FieldRules.rejectStandardMutation(true, false, false, false, false, true))
                .isInstanceOf(BusinessException.class);
        FieldRules.rejectStandardMutation(true, false, false, false, false, false);
        FieldRules.rejectStandardMutation(false, true, true, true, true, false);
    }

    @Test
    void retypeOutsideTheStorageClassIsRejected() {
        assertThatThrownBy(() -> FieldRules.rejectIncompatibleRetype(FieldType.TEXT, FieldType.NUMBER))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_TYPE_INCOMPATIBLE"));
        FieldRules.rejectIncompatibleRetype(FieldType.TEXT, FieldType.EMAIL);
    }

    @Test
    void valuesUseOnlyTheColumnForTheType() {
        assertThat(FieldRules.requireValue(FieldType.EMAIL, FieldRules.StoredValue.text("a@b.co")).text())
                .isEqualTo("a@b.co");
        assertThat(FieldRules.requireValue(FieldType.NUMBER, FieldRules.StoredValue.numeric(new BigDecimal("1.25")))
                .numeric()).isEqualByComparingTo("1.2500");
        assertThat(FieldRules.requireValue(FieldType.DATE, FieldRules.StoredValue.date(LocalDate.parse("2026-11-06")))
                .date()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(FieldRules.requireValue(FieldType.CHECKBOX, FieldRules.StoredValue.bool(false)).bool()).isFalse();
        assertThat(FieldRules.requireValue(FieldType.URL, FieldRules.StoredValue.text("https://example.com")).text())
                .isEqualTo("https://example.com");
        assertThatThrownBy(() -> FieldRules.requireValue(FieldType.EMAIL, FieldRules.StoredValue.text("not-an-email")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FieldRules.requireValue(FieldType.NUMBER, FieldRules.StoredValue.numeric(new BigDecimal("1.23456"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FieldRules.requireValue(FieldType.TEXT, FieldRules.StoredValue.numeric(BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void aValueNamesExactlyOneMatchingRecord() {
        assertThat(FieldRules.requireRecord(FieldEntityKind.ORGANIZATION, 4L, null, null)).isEqualTo(4L);
        assertThatThrownBy(() -> FieldRules.requireRecord(FieldEntityKind.CONTACT, 4L, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_RECORD_MISMATCH"));
        assertThatThrownBy(() -> FieldRules.requireRecord(FieldEntityKind.DEAL, null, 1L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clientSuppliedActorsAreRejected() {
        assertThatThrownBy(() -> FieldRules.rejectClientActor("spoofed", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("CLIENT_ACTOR_FORBIDDEN"));
        FieldRules.rejectClientActor(null, null, null, null);
    }
}
