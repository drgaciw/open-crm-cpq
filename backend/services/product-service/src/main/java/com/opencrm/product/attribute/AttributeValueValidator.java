package com.opencrm.product.attribute;

import com.opencrm.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Validates product attribute values against enum and regex rules at write time.
 */
@Component
public class AttributeValueValidator {

    public void validate(ProductAttribute attribute, String value) {
        if (value == null || value.isBlank()) {
            if (attribute.isRequired()) {
                throw new BusinessException(
                        "Attribute '" + attribute.getName() + "' is required",
                        "ATTRIBUTE_VALUE_REQUIRED"
                );
            }
            return;
        }

        if (attribute.getDataType() == AttributeDataType.ENUM) {
            validateEnumValue(attribute, value);
        }

        if (attribute.getValidationRegex() != null && !attribute.getValidationRegex().isBlank()) {
            validateRegex(attribute, value);
        }
    }

    private void validateEnumValue(ProductAttribute attribute, String value) {
        if (attribute.getEnumValues() == null || attribute.getEnumValues().isBlank()) {
            throw new BusinessException(
                    "Enum attribute '" + attribute.getName() + "' has no allowed values configured",
                    "ATTRIBUTE_ENUM_NOT_CONFIGURED"
            );
        }
        Set<String> allowed = Arrays.stream(attribute.getEnumValues().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (!allowed.contains(value)) {
            throw new BusinessException(
                    "Value '" + value + "' is not allowed for attribute '" + attribute.getName() + "'",
                    "ATTRIBUTE_ENUM_VALUE_INVALID"
            );
        }
    }

    private void validateRegex(ProductAttribute attribute, String value) {
        try {
            if (!Pattern.matches(attribute.getValidationRegex(), value)) {
                throw new BusinessException(
                        "Value does not match validation pattern for attribute '" + attribute.getName() + "'",
                        "ATTRIBUTE_VALUE_PATTERN_INVALID"
                );
            }
        } catch (PatternSyntaxException ex) {
            throw new BusinessException(
                    "Invalid validation regex configured for attribute '" + attribute.getName() + "'",
                    "ATTRIBUTE_VALIDATION_REGEX_INVALID"
            );
        }
    }
}
