package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VALUE_INVALID"));
        assertThatThrownBy(() -> FieldRules.requireValue(FieldType.TEXT, FieldRules.StoredValue.numeric(BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void numeric24Scale4AcceptsTwentyIntegerDigitsAndRejectsOverflow() {
        BigDecimal positiveBoundary = new BigDecimal("99999999999999999999.9999");
        BigDecimal negativeBoundary = positiveBoundary.negate();
        assertThat(acceptedNumber(positiveBoundary)).isEqualByComparingTo(positiveBoundary);
        assertThat(acceptedNumber(negativeBoundary)).isEqualByComparingTo(negativeBoundary);
        assertThat(acceptedNumber(BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
        assertThat(acceptedNumber(new BigDecimal("0E+20"))).isEqualByComparingTo("0.0000");
        assertThat(acceptedNumber(new BigDecimal("1E+19"))).isEqualByComparingTo("1E+19");
        assertThat(acceptedNumber(new BigDecimal("-1E+19"))).isEqualByComparingTo("-1E+19");

        assertInvalidNumber(new BigDecimal("123456789012345678901"));
        assertInvalidNumber(new BigDecimal("1E+20"));
        assertInvalidNumber(new BigDecimal("-1E+20"));
        assertInvalidNumber(new BigDecimal("1.23456"));
        assertInvalidNumber(new BigDecimal("1E-5"));
    }

    private static BigDecimal acceptedNumber(BigDecimal value) {
        return FieldRules.requireValue(FieldType.NUMBER, FieldRules.StoredValue.numeric(value)).numeric();
    }

    private static void assertInvalidNumber(BigDecimal value) {
        assertThatThrownBy(() -> FieldRules.requireValue(FieldType.NUMBER, FieldRules.StoredValue.numeric(value)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VALUE_INVALID"));
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
    void selectFieldsRequireOptionsAndOtherTypesRejectThem() {
        assertThatThrownBy(() -> FieldRules.requireSelectOptions(FieldType.SELECT, List.of()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_REQUIRED"));
        assertThatThrownBy(() -> FieldRules.requireSelectOptions(FieldType.SELECT, null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_REQUIRED"));
        assertThatThrownBy(() -> FieldRules.requireSelectOptions(FieldType.TEXT, List.of("Red")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_OPTION_INVALID"));
        FieldRules.requireSelectOptions(FieldType.SELECT, List.of("Red"));
        FieldRules.requireSelectOptions(FieldType.TEXT, null);
        FieldRules.requireSelectOptions(FieldType.TEXT, List.of());
    }

    @Test
    void phoneValuesAreAcceptedOrRejected() {
        assertThat(FieldRules.requireValue(FieldType.PHONE, FieldRules.StoredValue.text("+1 415 555 0100")).text())
                .isEqualTo("+1 415 555 0100");
        assertThat(FieldRules.requireValue(FieldType.PHONE, FieldRules.StoredValue.text("415-555-0100")).text())
                .isEqualTo("415-555-0100");
        assertInvalidValue(FieldType.PHONE, FieldRules.StoredValue.text("12345"));
        assertInvalidValue(FieldType.PHONE, FieldRules.StoredValue.text("(415) 555-0100"));
        assertInvalidValue(FieldType.PHONE, FieldRules.StoredValue.text("++1234567"));
        assertInvalidValue(FieldType.PHONE, FieldRules.StoredValue.text("phone"));
    }

    @Test
    void urlsAcceptHttpAndRejectFtpMissingHostAndMalformed() {
        assertThat(FieldRules.requireValue(FieldType.URL, FieldRules.StoredValue.text("http://example.com")).text())
                .isEqualTo("http://example.com");
        assertInvalidValue(FieldType.URL, FieldRules.StoredValue.text("ftp://example.com/a"));
        assertInvalidValue(FieldType.URL, FieldRules.StoredValue.text("http:///path"));
        assertInvalidValue(FieldType.URL, FieldRules.StoredValue.text("http://["));
    }

    @Test
    void labelRejectsEmptyNullAndTooLong() {
        assertInvalidLabel("");
        assertInvalidLabel(null);
        assertInvalidLabel("a".repeat(256));
        assertThat(FieldRules.requireLabel("a".repeat(255))).hasSize(255);
        assertThat(FieldRules.requireLabel("  Region  ")).isEqualTo("Region");
    }

    @Test
    void positionRejectsValuesOutsideTheRange() {
        assertThatThrownBy(() -> FieldRules.requirePosition(-1))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_POSITION_INVALID"));
        assertThatThrownBy(() -> FieldRules.requirePosition(100_001))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_POSITION_INVALID"));
        FieldRules.requirePosition(0);
        FieldRules.requirePosition(100_000);
    }

    @Test
    void briefBlankBecomesNullAndTooLongIsRejected() {
        assertThat(FieldRules.normalizeBrief(null)).isNull();
        assertThat(FieldRules.normalizeBrief("")).isNull();
        assertThat(FieldRules.normalizeBrief("   ")).isNull();
        assertThat(FieldRules.normalizeBrief("  Find the region  ")).isEqualTo("Find the region");
        assertThatThrownBy(() -> FieldRules.normalizeBrief("b".repeat(FieldRules.TEXT_MAX + 1)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_BRIEF_INVALID"));
    }

    @Test
    void missingDateCheckboxOptionAndUserValuesAreRejected() {
        assertInvalidValue(FieldType.DATE, FieldRules.StoredValue.date(null));
        assertInvalidValue(FieldType.CHECKBOX, FieldRules.StoredValue.bool(null));
        assertInvalidValue(FieldType.SELECT, FieldRules.StoredValue.option(null));
        assertInvalidValue(FieldType.USER, FieldRules.StoredValue.user(null));
        assertInvalidValue(FieldType.USER, FieldRules.StoredValue.user("   "));
    }

    @Test
    void textAndLongTextRejectValuesOverTheirMax() {
        assertThat(FieldRules.requireValue(FieldType.TEXT,
                FieldRules.StoredValue.text("a".repeat(FieldRules.TEXT_MAX))).text())
                .hasSize(FieldRules.TEXT_MAX);
        assertInvalidValue(FieldType.TEXT, FieldRules.StoredValue.text("a".repeat(FieldRules.TEXT_MAX + 1)));
        assertThat(FieldRules.requireValue(FieldType.LONG_TEXT,
                FieldRules.StoredValue.text("a".repeat(FieldRules.LONG_TEXT_MAX))).text())
                .hasSize(FieldRules.LONG_TEXT_MAX);
        assertInvalidValue(FieldType.LONG_TEXT, FieldRules.StoredValue.text("a".repeat(FieldRules.LONG_TEXT_MAX + 1)));
    }

    private static void assertInvalidLabel(String label) {
        assertThatThrownBy(() -> FieldRules.requireLabel(label))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_LABEL_INVALID"));
    }

    private static void assertInvalidValue(FieldType type, FieldRules.StoredValue value) {
        assertThatThrownBy(() -> FieldRules.requireValue(type, value))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("FIELD_VALUE_INVALID"));
    }

}
